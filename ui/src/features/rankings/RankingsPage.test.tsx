import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import RankingsPage from './RankingsPage';
import type { League, Rankings, Season, Tournament } from '@/shared/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const rankingsMock = vi.fn();
const tournamentsMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: (id: string, live: boolean, through?: string) =>
      rankingsMock(id, live, through),
    tournaments: (id: string) => tournamentsMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

const league: League = { id: 'lg-1', name: 'Test League', createdAt: '2026-01-01T00:00:00Z' };
const season: Season = {
  id: 'sn-1',
  leagueId: 'lg-1',
  name: 'Season 1',
  seasonYear: 2026,
  seasonNumber: 1,
  status: 'active',
  tieFloor: 1,
  sideBetAmount: 15,
  maxTeams: 13,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
const tournament: Tournament = {
  id: 'tn-7',
  pgaTournamentId: null,
  name: 'Sample Open',
  seasonId: 'sn-1',
  startDate: '2026-03-01',
  endDate: '2026-03-04',
  courseName: null,
  status: 'completed',
  purseAmount: null,
  payoutMultiplier: 1,
  week: '9',
  createdAt: '2026-01-01T00:00:00Z',
};
const rankings: Rankings = {
  teams: [
    {
      teamId: 'team-1',
      teamName: 'Aces',
      subtotal: 120,
      sideBets: 30,
      totalCash: 150,
      series: [50, 100, 150],
      liveWeekly: null,
    },
  ],
  weeks: ['9', '10', '11'],
  tournamentNames: ['A', 'B', 'C'],
  live: false,
};

describe('RankingsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    rankingsMock.mockReset();
    tournamentsMock.mockReset();
  });

  it('loads the full season rankings by default', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />);

    expect(await screen.findByRole('heading', { name: /Team Standings/i })).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('Aces')).toBeInTheDocument();
    expect(rankingsMock).toHaveBeenCalledWith('sn-1', true, undefined);
  });

  it('passes the selected tournament as the "through" parameter', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    rankingsMock.mockResolvedValue(rankings);

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />);

    await screen.findByRole('heading', { name: /Team Standings/i });
    const select = screen.getByLabelText(/Through/i);
    await user.selectOptions(select, 'tn-7');

    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', true, 'tn-7');
  });

  it('shows an error message when rankings fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    rankingsMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RankingsPage />);
    expect(await screen.findByText(/Failed to load standings/i)).toBeInTheDocument();
  });
});
