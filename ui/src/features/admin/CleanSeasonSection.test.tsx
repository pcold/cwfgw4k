import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryKey } from '@tanstack/react-query';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import OpenQuery from '@/shared/test/OpenQuery';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeSeason } from '@/shared/test/fixtures';
import CleanSeasonSection from './CleanSeasonSection';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const cleanMock = vi.fn<typeof api.cleanSeasonResults>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    cleanSeasonResults: (id: string) => cleanMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

// Each key is the one its page holds: ScoreboardPage, WeeklyReportPage (and LateRowBetsPage),
// RankingsPage, PlayerRankingsPage, GolferHistoryModal.
const OPEN_WHEN_A_CLEAN_LANDS: [string, QueryKey][] = [
  ['tournamentReport', queryKeys.tournamentReport.detail('sn-1', 'tn-1', false)],
  ['report', queryKeys.report.detail('sn-1', 'tn-1', false)],
  ['rankings', queryKeys.rankings.detail('sn-1', false, 'tn-1')],
  ['playerRankings', queryKeys.playerRankings.detail('sn-1', 'tn-1', false)],
  ['golferHistory', queryKeys.golferHistory.detail('sn-1', 'g-1')],
];

describe('CleanSeasonSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    cleanMock.mockReset();
    leaguesMock.mockResolvedValue([{ id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' }]);
    seasonsMock.mockResolvedValue([makeSeason()]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('cleans the selected season after confirm', async () => {
    cleanMock.mockResolvedValue({
      scoresDeleted: 12,
      resultsDeleted: 3,
      tournamentsReset: 2,
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<CleanSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to clean/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Clean Results/i }));

    await waitFor(() => {
      expect(cleanMock).toHaveBeenCalledWith('sn-1');
    });
    expect(await screen.findByText(/Cleaned 12 scores, 3 results; reset 2 tournaments\./)).toBeInTheDocument();
  });

  it.each(OPEN_WHEN_A_CLEAN_LANDS)('refetches an open %s query after a clean', async (_root, queryKey) => {
    const fetcher = vi.fn().mockResolvedValue({});
    cleanMock.mockResolvedValue({
      scoresDeleted: 12,
      resultsDeleted: 3,
      tournamentsReset: 2,
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(
      <>
        <CleanSeasonSection />
        <OpenQuery queryKey={queryKey} fetcher={fetcher} />
      </>,
      { withLeagueSeasonProvider: false },
    );

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });
    expect(fetcher).toHaveBeenCalledTimes(1);

    await user.selectOptions(screen.getByLabelText(/Season to clean/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Clean Results/i }));

    await waitFor(() => {
      expect(fetcher).toHaveBeenCalledTimes(2);
    });
  });

  it('does nothing when the user cancels the confirm dialog', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const user = userEvent.setup();
    renderWithProviders(<CleanSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to clean/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Clean Results/i }));

    expect(cleanMock).not.toHaveBeenCalled();
  });

  it('cleans a season in the league the admin picks, not only the first', async () => {
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'lg-2', name: 'Beta', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockImplementation((leagueId) =>
      Promise.resolve(
        leagueId === 'lg-2' ? [makeSeason({ id: 'sn-beta', leagueId: 'lg-2', name: 'Fall' })] : [makeSeason()],
      ),
    );
    cleanMock.mockResolvedValue({ scoresDeleted: 1, resultsDeleted: 1, tournamentsReset: 1 });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<CleanSeasonSection />, { withLeagueSeasonProvider: false });

    await screen.findByRole('option', { name: 'Beta' });
    await user.selectOptions(screen.getByLabelText('League'), 'lg-2');
    await screen.findByRole('option', { name: /^2026 Fall$/ });
    expect(screen.queryByRole('option', { name: /^2026 Spring$/ })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/Season to clean/i), 'sn-beta');
    await user.click(screen.getByRole('button', { name: /Clean Results/i }));

    await waitFor(() => {
      expect(cleanMock).toHaveBeenCalledWith('sn-beta');
    });
    expect(seasonsMock).toHaveBeenCalledWith('lg-2');
  });

  it('names the season picker with a visible label', () => {
    renderWithProviders(<CleanSeasonSection />, { withLeagueSeasonProvider: false });
    expect(screen.getByText('Season to clean')).toBeInTheDocument();
  });
});
