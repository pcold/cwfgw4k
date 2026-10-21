import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryKey } from '@tanstack/react-query';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import OpenQuery from '@/shared/test/OpenQuery';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeSeason, makeTournament } from '@/shared/test/fixtures';
import ResetTournamentSection from './ResetTournamentSection';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const resettableTournamentsMock = vi.fn<typeof api.resettableTournaments>();
const resetTournamentMock = vi.fn<typeof api.resetTournament>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    resettableTournaments: (id: string) => resettableTournamentsMock(id),
    resetTournament: (id: string) => resetTournamentMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

// Each key is the one its page holds: ScoreboardPage, WeeklyReportPage (and LateRowBetsPage),
// RankingsPage, PlayerRankingsPage, GolferHistoryModal.
const OPEN_WHEN_A_RESET_LANDS: [string, QueryKey][] = [
  ['tournamentReport', queryKeys.tournamentReport.detail('sn-1', 'tn-1', false)],
  ['report', queryKeys.report.detail('sn-1', 'tn-1', false)],
  ['rankings', queryKeys.rankings.detail('sn-1', false, 'tn-1')],
  ['playerRankings', queryKeys.playerRankings.detail('sn-1', 'tn-1', false)],
  ['golferHistory', queryKeys.golferHistory.detail('sn-1', 'g-1')],
];

describe('ResetTournamentSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    resettableTournamentsMock.mockReset();
    resetTournamentMock.mockReset();
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockResolvedValue([makeSeason()]);
    resettableTournamentsMock.mockResolvedValue([
      makeTournament({ id: 'tn-1', name: 'Sony Open', startDate: '2026-01-15', endDate: '2026-01-18', week: '1' }),
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("lists exactly the season's tournaments the server says reset accepts, and resets on confirm", async () => {
    resetTournamentMock.mockResolvedValue({
      id: 'tn-1',
      pgaTournamentId: null,
      name: 'Sony Open',
      seasonId: 'sn-1',
      startDate: '2026-01-08',
      endDate: '2026-01-11',
      courseName: null,
      status: 'upcoming',
      purseAmount: null,
      payoutMultiplier: 1,
      week: '1',
      isTeamEvent: false,
      createdAt: '2026-01-01T00:00:00Z',
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });
    expect(resettableTournamentsMock).toHaveBeenCalledWith('sn-1');
    const picker = screen.getByLabelText(/Completed tournament/i);
    expect(within(picker).getAllByRole('option').map((option) => option.textContent)).toEqual([
      '-- select completed tournament --',
      'Wk 1 — Sony Open',
    ]);

    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    await user.click(screen.getByRole('button', { name: /Reset Results/i }));

    await waitFor(() => {
      expect(resetTournamentMock).toHaveBeenCalledWith('tn-1');
    });
    expect(
      await screen.findByText(/Reset "Sony Open" to upcoming\./),
    ).toBeInTheDocument();
  });

  it.each(OPEN_WHEN_A_RESET_LANDS)(
    'refetches an open %s query after a reset',
    async (_root, queryKey) => {
      const fetcher = vi.fn().mockResolvedValue({});
      resetTournamentMock.mockResolvedValue(makeTournament({ name: 'Sony Open', status: 'upcoming' }));
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      const user = userEvent.setup();
      renderWithProviders(
        <>
          <ResetTournamentSection />
          <OpenQuery queryKey={queryKey} fetcher={fetcher} />
        </>,
        { withLeagueSeasonProvider: false },
      );

      await waitFor(() => {
        expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
      });
      expect(fetcher).toHaveBeenCalledTimes(1);

      await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
      await user.click(screen.getByRole('button', { name: /Reset Results/i }));

      await waitFor(() => {
        expect(fetcher).toHaveBeenCalledTimes(2);
      });
    },
  );

  it('asks the server for the resettable list again after a reset', async () => {
    resetTournamentMock.mockResolvedValue(makeTournament({ name: 'Sony Open', status: 'upcoming' }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });
    expect(resettableTournamentsMock).toHaveBeenCalledTimes(1);

    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    await user.click(screen.getByRole('button', { name: /Reset Results/i }));

    await waitFor(() => {
      expect(resettableTournamentsMock).toHaveBeenCalledTimes(2);
    });
  });

  it('does not call the API when the user cancels the confirm dialog', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    await user.click(screen.getByRole('button', { name: /Reset Results/i }));

    expect(resetTournamentMock).not.toHaveBeenCalled();
  });

  it('drops the picked tournament when the season changes', async () => {
    seasonsMock.mockResolvedValue([makeSeason(), makeSeason({ id: 'sn-2', name: 'Fall' })]);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    expect(screen.getByRole('button', { name: /Reset Results/i })).toBeEnabled();

    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-2');

    expect(screen.getByLabelText(/Completed tournament/i)).toHaveValue('');
    expect(screen.getByRole('button', { name: /Reset Results/i })).toBeDisabled();
  });

  it('lists the resettable tournaments of a season in the league the admin picks, not only the first', async () => {
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'lg-2', name: 'Beta', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockImplementation((leagueId) =>
      Promise.resolve(
        leagueId === 'lg-2'
          ? [makeSeason({ id: 'sn-beta', leagueId: 'lg-2', name: 'Fall' })]
          : [makeSeason()],
      ),
    );
    resettableTournamentsMock.mockImplementation((seasonId) =>
      Promise.resolve(
        seasonId === 'sn-beta'
          ? [makeTournament({ id: 'tn-beta', seasonId: 'sn-beta', name: 'Fall Classic', week: '2' })]
          : [makeTournament({ id: 'tn-1', name: 'Sony Open', week: '1' })],
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await screen.findByRole('option', { name: /Wk 1 — Sony Open/ });
    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    await user.selectOptions(screen.getByLabelText('League'), 'lg-2');

    await screen.findByRole('option', { name: /Wk 2 — Fall Classic/ });
    expect(screen.queryByRole('option', { name: /Sony Open/ })).not.toBeInTheDocument();
    expect(seasonsMock).toHaveBeenCalledWith('lg-2');
    expect(resettableTournamentsMock).toHaveBeenCalledWith('sn-beta');
    expect(screen.getByRole('button', { name: /Reset Results/i })).toBeDisabled();
  });

  it('names both pickers with visible labels', () => {
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });
    expect(screen.getByText('Season')).toBeInTheDocument();
    expect(screen.getByText('Completed tournament')).toBeInTheDocument();
  });
});
