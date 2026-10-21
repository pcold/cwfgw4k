import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { nth } from '@/shared/test/nth';
import { pageTitleRow } from '@/shared/test/pageTitleRow';
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
  {
    teamId: 't-2',
    teamNumber: 2,
    teamName: 'Birdies',
    picks: [
      { round: 1, golferName: 'Jon Rahm', ownershipPct: 100, golferId: 'g-2' },
      { round: 2, golferName: 'Rory McIlroy', ownershipPct: 100, golferId: 'g-3' },
    ],
  },
];

const MY_TEAM_KEY = 'cwfg.myTeam';
const WITH_MY_TEAM = { withMyTeamProvider: true };

async function findGrid(): Promise<HTMLElement> {
  return screen.findByRole('table');
}

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
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it("tags the late-row rounds the season's rules name", async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);

    renderWithProviders(<RostersPage />, WITH_MY_TEAM);

    const grid = await findGrid();
    await waitFor(() => {
      const roundRows = within(grid).getAllByRole('row').slice(1);
      const tagged = roundRows.map((row) => within(row).queryByText('Late row') !== null);
      expect(tagged).toEqual([false, true, true, false, false, false, false, false]);
    });
    expect(seasonRulesMock).toHaveBeenCalledWith('sn-1');
  });

  it('still shows the rosters when the rules fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);
    seasonRulesMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RostersPage />, WITH_MY_TEAM);

    expect(within(await findGrid()).getByText('Scottie Scheffler')).toBeInTheDocument();
  });

  it('renders the roster grid when all queries succeed', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);

    renderWithProviders(<RostersPage />, WITH_MY_TEAM);

    expect(within(await findGrid()).getByText('Scottie Scheffler')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Team Rosters/i })).toBeInTheDocument();
    expect(rostersMock).toHaveBeenCalledWith('sn-1');
    expect(pageTitleRow(await screen.findByRole('button', { name: '2026 Season 1' }))).toContainElement(
      screen.getByRole('heading', { name: 'Team Rosters' }),
    );
  });

  it('shows an error message when rosters fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RostersPage />, WITH_MY_TEAM);
    expect(await screen.findByText(/Failed to load rosters/i)).toBeInTheDocument();
  });

  it('switches the phone cards to the team tapped on the strip', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    rostersMock.mockResolvedValue(teams);
    const user = userEvent.setup();

    renderWithProviders(<RostersPage />, WITH_MY_TEAM);

    const strip = await screen.findByRole('group', { name: 'Team' });
    const picks = screen.getByRole('list', { name: 'Picks' });
    expect(within(picks).getByText('Scottie Scheffler')).toBeInTheDocument();

    await user.click(within(strip).getByRole('button', { name: 'Birdies' }));

    expect(within(strip).getByRole('button', { name: 'Birdies' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(strip).getByRole('button', { name: 'Aces' })).toHaveAttribute('aria-pressed', 'false');
    const switched = screen.getByRole('list', { name: 'Picks' });
    expect(within(switched).getByText('Jon Rahm')).toBeInTheDocument();
    expect(within(switched).queryByText('Scottie Scheffler')).not.toBeInTheDocument();
  });

  describe("the strip on the visitor's own team", () => {
    function pressedChip(): string | null {
      const pressed = within(screen.getByRole('group', { name: 'Team' }))
        .getAllByRole('button')
        .find((chip) => chip.getAttribute('aria-pressed') === 'true');
      return pressed?.textContent ?? null;
    }

    async function renderRosters(): Promise<void> {
      leaguesMock.mockResolvedValue([league]);
      seasonsMock.mockResolvedValue([season]);
      rostersMock.mockResolvedValue(teams);
      renderWithProviders(<RostersPage />, WITH_MY_TEAM);
      await screen.findByRole('group', { name: 'Team' });
    }

    it('opens on the saved team', async () => {
      window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"BIRDIES"}');
      await renderRosters();

      expect(pressedChip()).toBe('Birdies');
      expect(within(screen.getByRole('list', { name: 'Picks' })).getByText('Jon Rahm')).toBeInTheDocument();
    });

    it('switches to a tapped team', async () => {
      window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"BIRDIES"}');
      const user = userEvent.setup();
      await renderRosters();

      await user.click(within(screen.getByRole('group', { name: 'Team' })).getByRole('button', { name: 'Aces' }));

      expect(pressedChip()).toBe('Aces');
    });

    it.each([
      ['browsing', '{"kind":"browsing"}'],
      ['never asked', null],
    ])('opens on the first team when %s', async (_choice, stored) => {
      if (stored !== null) window.localStorage.setItem(MY_TEAM_KEY, stored);
      await renderRosters();

      expect(pressedChip()).toBe('Aces');
    });

    it('opens an older season without the saved team on its first team', async () => {
      const older: Season = { ...season, id: 'sn-0', name: 'Season 0', seasonYear: 2025 };
      const olderTeams: RosterTeam[] = [
        { ...nth(teams, 0), teamId: 't-8', teamName: 'Condors' },
        { ...nth(teams, 1), teamId: 't-9', teamName: 'Eagles' },
      ];
      window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"BIRDIES"}');
      leaguesMock.mockResolvedValue([league]);
      seasonsMock.mockResolvedValue([season, older]);
      rostersMock.mockImplementation((seasonId) => Promise.resolve(seasonId === 'sn-0' ? olderTeams : teams));
      const user = userEvent.setup();
      renderWithProviders(<RostersPage />, WITH_MY_TEAM);
      await screen.findByRole('group', { name: 'Team' });
      expect(pressedChip()).toBe('Birdies');

      await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-0');

      await waitFor(() => expect(pressedChip()).toBe('Condors'));
    });
  });
});
