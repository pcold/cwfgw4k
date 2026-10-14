import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import type { api } from '@/shared/api/client';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import type { League, RosterTeam, Season, SeasonRules } from '@/shared/api/types';
import RostersPage from './RostersPage';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const rostersMock = vi.fn<typeof api.rosters>();
const seasonRulesMock = vi.fn<typeof api.seasonRules>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: vi.fn(),
    rosters: (id: string) => rostersMock(id),
    seasonRules: (id: string) => seasonRulesMock(id),
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
const teams: RosterTeam[] = [
  {
    teamId: 't-1',
    teamNumber: 1,
    teamName: 'Aces',
    picks: [
      { round: 1, golferName: 'Scottie Scheffler', ownershipPct: 100, golferId: 'g-1' },
    ],
  },
];

const rules: SeasonRules = {
  payouts: [18, 12, 10, 8, 7, 6, 5, 4, 3, 2],
  tieFloor: 1,
  sideBetRounds: [2, 3],
  sideBetAmount: 20,
};

describe('RostersPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    rostersMock.mockReset();
    seasonRulesMock.mockReset();
    seasonRulesMock.mockResolvedValue(rules);
  });

  it("sets apart the side-bet rounds the season's rules name", async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);

    renderWithProviders(<RostersPage />);

    await screen.findByText('Scottie Scheffler');
    await waitFor(() => {
      const roundRows = screen.getAllByRole('row').slice(1);
      const setApart = roundRows.map((row) => row.classList.contains('bg-gray-800/70'));
      expect(setApart).toEqual([false, true, true, false, false, false, false, false]);
    });
    expect(seasonRulesMock).toHaveBeenCalledWith('sn-1');
  });

  it('still shows the rosters when the rules fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);
    seasonRulesMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RostersPage />);

    expect(await screen.findByText('Scottie Scheffler')).toBeInTheDocument();
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
