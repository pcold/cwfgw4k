import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useQuery, type QueryKey } from '@tanstack/react-query';
import ResetTournamentSection from './ResetTournamentSection';
import { queryKeys } from '@/shared/api/queryKeys';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const resetTournamentMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    tournaments: (id: string) => tournamentsMock(id),
    resetTournament: (id: string) => resetTournamentMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

function OpenQuery({ queryKey, fetcher }: { queryKey: QueryKey; fetcher: () => Promise<unknown> }) {
  useQuery({ queryKey, queryFn: fetcher });
  return null;
}

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
    tournamentsMock.mockReset();
    resetTournamentMock.mockReset();
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockResolvedValue([
      {
        id: 'sn-1',
        leagueId: 'lg-1',
        name: 'Spring',
        seasonYear: 2026,
        seasonNumber: 1,
        status: 'active',
      },
    ]);
    tournamentsMock.mockResolvedValue([
      {
        id: 'tn-1',
        name: 'Sony Open',
        seasonId: 'sn-1',
        startDate: '2026-01-15',
        endDate: '2026-01-18',
        status: 'completed',
        payoutMultiplier: 1,
        week: '1',
        pgaTournamentId: null,
        courseName: null,
        purseAmount: null,
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'tn-2',
        name: 'Upcoming Open',
        seasonId: 'sn-1',
        startDate: '2026-05-15',
        endDate: '2026-05-18',
        status: 'upcoming',
        payoutMultiplier: 1,
        week: '20',
        pgaTournamentId: null,
        courseName: null,
        purseAmount: null,
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists only completed tournaments and resets on confirm', async () => {
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
      createdAt: '2026-01-01T00:00:00Z',
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });
    expect(screen.queryByRole('option', { name: /Upcoming Open/ })).not.toBeInTheDocument();

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
      resetTournamentMock.mockResolvedValue({ id: 'tn-1', name: 'Sony Open', status: 'upcoming' });
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
});
