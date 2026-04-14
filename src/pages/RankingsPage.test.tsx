import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import RankingsPage from './RankingsPage';
import type { League, Rankings, Season } from '../api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const rankingsMock = vi.fn();

vi.mock('../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: (id: string, live: boolean) => rankingsMock(id, live),
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
};
const rankings: Rankings = {
  teams: [
    {
      teamId: 'team-1',
      teamName: 'Aces',
      subtotal: 120,
      sideBets: 30,
      totalCash: 150,
      series: [],
      liveWeekly: null,
    },
  ],
  weeks: ['Week 1'],
  tournamentNames: ['Sample'],
  live: false,
};

describe('RankingsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    rankingsMock.mockReset();
  });

  it('renders the rankings table when all queries succeed', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />);

    expect(await screen.findByRole('heading', { name: /Team Standings/i })).toBeInTheDocument();
    expect(screen.getByText('Aces')).toBeInTheDocument();
    expect(rankingsMock).toHaveBeenCalledWith('sn-1', false);
  });

  it('shows an error message when rankings fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rankingsMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RankingsPage />);
    expect(await screen.findByText(/Failed to load standings/i)).toBeInTheDocument();
  });
});
