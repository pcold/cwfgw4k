import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import RostersPage from './RostersPage';
import type { League, RosterTeam, Season } from '@/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const rostersMock = vi.fn();

vi.mock('../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: vi.fn(),
    rosters: (id: string) => rostersMock(id),
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
const teams: RosterTeam[] = [
  {
    teamId: 't-1',
    teamName: 'Aces',
    picks: [
      { round: 1, golferName: 'Scottie Scheffler', ownershipPct: 100, golferId: 'g-1' },
    ],
  },
];

describe('RostersPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    rostersMock.mockReset();
  });

  it('renders the roster grid when all queries succeed', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);

    renderWithProviders(<RostersPage />);

    expect(await screen.findByRole('heading', { name: /Team Rosters/i })).toBeInTheDocument();
    expect(screen.getByText('Scottie Scheffler')).toBeInTheDocument();
    expect(rostersMock).toHaveBeenCalledWith('sn-1');
  });

  it('shows an error message when rosters fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RostersPage />);
    expect(await screen.findByText(/Failed to load rosters/i)).toBeInTheDocument();
  });
});
