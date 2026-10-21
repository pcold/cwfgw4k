import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/shared/api/client';
import type { League, RosterTeam, Season } from '@/shared/api/types';
import { pageTitleRow } from '@/shared/test/pageTitleRow';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import App from './App';

vi.mock('@/shared/api/client', () => ({
  api: {
    authMe: vi.fn().mockResolvedValue(null),
    leagues: vi.fn().mockResolvedValue([]),
    seasons: vi.fn().mockResolvedValue([]),
    seasonReport: vi.fn().mockResolvedValue({
      scope: 'season',
      tournament: {
        id: null,
        name: null,
        startDate: null,
        endDate: null,
        status: null,
        payoutMultiplier: 1,
        week: null,
      },
      teams: [],
      undraftedTopTens: [],
      sideBetRules: null,
      sideBetDetail: [],
      standingsOrder: [],
      live: false,
      liveLeaderboard: [],
      cashTotals: { won: 0, lost: 0, net: 0 },
      sideBetTotals: { won: 0, lost: 0, net: 0 },
      weeklyTotals: { won: 0, lost: 0, net: 0 },
      weeklyStandings: [],
    }),
    rankings: vi.fn().mockResolvedValue({
      teams: [],
      weeks: [],
      tournamentNames: [],
      live: false,
    }),
    rosters: vi.fn().mockResolvedValue([]),
    seasonRules: vi.fn().mockReturnValue(new Promise(() => {})),
    tournaments: vi.fn().mockResolvedValue([]),
    tournamentReport: vi.fn().mockResolvedValue(null),
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

const mainNavigation = (): HTMLElement => screen.getByRole('navigation', { name: 'Main' });

const MY_TEAM_KEY = 'cwfg.myTeam';

const rosterTeam = (teamName: string): RosterTeam => ({ teamId: `tm-${teamName}`, teamNumber: null, teamName, picks: [] });

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('renders the site heading', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(screen.getByRole('heading', { name: 'Castlewood FG' })).toBeInTheDocument();
  });

  it("marks the current page's nav link, and only that one, as the current page", () => {
    renderWithProviders(<App />, { initialPath: '/rankings', withLeagueSeasonProvider: false });

    const currentLinks = within(mainNavigation())
      .getAllByRole('link')
      .filter((link) => link.hasAttribute('aria-current'));
    expect(currentLinks.map((link) => [link.textContent, link.getAttribute('aria-current')])).toEqual([
      ['Standings', 'page'],
    ]);
  });

  it('gives every desktop nav link the tap-target height, its text centred in it', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    for (const link of within(mainNavigation()).getAllByRole('link')) {
      expect(link).toHaveClass(TAP_TARGET_CLASS, 'inline-flex', 'items-center');
    }
  });

  it.each([
    ['Scoreboard', '/scoreboard'],
    ['Report', '/report'],
    ['Standings', '/rankings'],
    ['Players', '/player-rankings'],
    ['Rosters', '/rosters'],
    ['Bets', '/late-row-bets'],
    ['Rules', '/rules'],
  ])('links the %s nav item to %s', (label, path) => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(within(mainNavigation()).getByRole('link', { name: label })).toHaveAttribute('href', path);
  });

  it('keeps Admin out of the main nav', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(within(mainNavigation()).queryByRole('link', { name: /admin/i })).toBeNull();
  });

  it('has no hamburger menu button', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(screen.queryByRole('button', { name: /navigation menu/i })).toBeNull();
  });

  it.each(['/', '/scoreboard', '/report', '/rankings', '/player-rankings', '/rosters', '/late-row-bets', '/rules', '/admin'])(
    'shows the tab bar on %s',
    (path) => {
      renderWithProviders(<App />, { initialPath: path, withLeagueSeasonProvider: false });

      expect(screen.getByRole('navigation', { name: 'Tabs' })).toBeInTheDocument();
    },
  );

  it.each(['/', '/scoreboard', '/report', '/rankings', '/player-rankings', '/rosters', '/late-row-bets', '/rules'])(
    "puts %s's picker button in the main nav below sm, opening the page's picker sheet",
    async (path) => {
      vi.mocked(api.leagues).mockResolvedValueOnce([league]);
      vi.mocked(api.seasons).mockResolvedValueOnce([season]);
      const user = userEvent.setup();
      renderWithProviders(<App />, { initialPath: path, withLeagueSeasonProvider: false });

      const weekButton = await within(mainNavigation()).findByRole('button', {
        name: '2026 Season 1, change league and season',
      });
      expect(weekButton.parentElement).toHaveClass('sm:hidden');
      await user.click(weekButton);

      expect(screen.getByRole('dialog', { name: 'League and season' })).toBeInTheDocument();
    },
  );

  it.each(['/admin', '/no-such-page'])('leaves the main nav without a picker button on %s', async (path) => {
    vi.mocked(api.leagues).mockResolvedValueOnce([league]);
    vi.mocked(api.seasons).mockResolvedValueOnce([season]);
    const seasonsCallsBefore = vi.mocked(api.seasons).mock.calls.length;
    renderWithProviders(<App />, { initialPath: path, withLeagueSeasonProvider: false });

    // The seasons request follows the leagues, so the picker has had its leagues by the time it fires.
    await waitFor(() => {
      expect(vi.mocked(api.seasons).mock.calls.length).toBeGreaterThan(seasonsCallsBefore);
    });
    expect(within(mainNavigation()).queryByRole('button')).toBeNull();
  });

  it('pads the foot of main past the tab bar below lg, and keeps the page padding from lg', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(screen.getByRole('main')).toHaveClass('pt-4', 'sm:pt-6', 'pb-above-tab-bar-4', 'sm:pb-above-tab-bar-6', 'lg:pb-6');
  });

  it('hides the footer below lg, where the More sheet reaches Admin', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(screen.getByRole('contentinfo')).toHaveClass('hidden', 'lg:block');
  });

  it('follows a More sheet link to its page and closes the sheet', async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { initialPath: '/scoreboard', withLeagueSeasonProvider: false });

    await user.click(screen.getByRole('button', { name: 'More' }));
    await user.click(within(screen.getByRole('dialog', { name: 'More' })).getByRole('link', { name: 'Rules' }));

    expect(await screen.findByRole('heading', { level: 2, name: 'Season Rules' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('links Admin from the site footer to its unchanged path', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    const footer = screen.getByRole('contentinfo');
    expect(within(footer).getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');
  });

  it('gives the footer Admin link the tap-target height, its text centred in it', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    const footer = screen.getByRole('contentinfo');
    expect(within(footer).getByRole('link', { name: 'Admin' })).toHaveClass(TAP_TARGET_CLASS, 'inline-flex', 'items-center');
  });

  it.each([
    ['/scoreboard', 'Scoreboard'],
    ['/report', 'Weekly Report'],
    ['/rankings', 'Team Standings'],
    ['/player-rankings', 'Player Rankings'],
    ['/rosters', 'Team Rosters'],
    ['/late-row-bets', 'Late Row Bets'],
    ['/rules', 'Season Rules'],
  ])("puts the league and season picker in %s's title row, hidden below sm, and nowhere else", async (path, title) => {
    vi.mocked(api.leagues).mockResolvedValueOnce([league]);
    vi.mocked(api.seasons).mockResolvedValueOnce([season]);
    renderWithProviders(<App />, { initialPath: path, withLeagueSeasonProvider: false });

    const leagueSelect = await screen.findByRole('combobox', { name: 'League' });
    const titleRow = pageTitleRow(screen.getByRole('heading', { level: 2, name: title }));
    expect(titleRow).toContainElement(leagueSelect);
    expect(within(titleRow).getByRole('combobox', { name: 'Season' })).toBeInTheDocument();
    const desktopWrapper = leagueSelect.closest<HTMLElement>('.hidden');
    expect(desktopWrapper).toHaveClass('sm:block');
    expect(titleRow).toContainElement(desktopWrapper);
    expect(screen.getAllByRole('combobox', { name: /^(League|Season)$/ })).toHaveLength(2);
  });
});

describe('App my team', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.mocked(api.leagues).mockResolvedValue([league]);
    vi.mocked(api.seasons).mockResolvedValue([season]);
    vi.mocked(api.rosters).mockResolvedValue(['Womble', 'BLAU', 'Juray'].map(rosterTeam));
  });

  afterEach(() => {
    window.localStorage.clear();
    vi.mocked(api.leagues).mockResolvedValue([]);
    vi.mocked(api.seasons).mockResolvedValue([]);
    vi.mocked(api.rosters).mockResolvedValue([]);
  });

  const picker = (): Promise<HTMLElement> => screen.findByRole('dialog', { name: 'Which team is yours?' });

  it('asks a first-time visitor which team is theirs', async () => {
    renderWithProviders(<App />, { initialPath: '/rankings', withLeagueSeasonProvider: false });

    expect(await picker()).toBeInTheDocument();
  });

  it('does not ask on Admin', async () => {
    renderWithProviders(<App />, { initialPath: '/admin', withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(api.rosters).toHaveBeenCalledWith(season.id);
    });
    await act(async () => {
      await vi.mocked(api.rosters).mock.results.at(-1)?.value;
      await new Promise((resolve) => {
        setTimeout(resolve, 0);
      });
    });
    expect(screen.queryByRole('dialog', { name: 'Which team is yours?' })).toBeNull();
  });

  it('clears the saved team from More and asks again', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const user = userEvent.setup();
    renderWithProviders(<App />, { initialPath: '/scoreboard', withLeagueSeasonProvider: false });

    await user.click(screen.getByRole('button', { name: 'More' }));
    const more = screen.getByRole('dialog', { name: 'More' });
    await user.click(await within(more).findByRole('button', { name: 'My team: WOMBLE · Change' }));

    expect(await picker()).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'More' })).toBeNull();
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
  });

  it('returns focus to the More tab when the picker opened from its sheet closes', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const user = userEvent.setup();
    renderWithProviders(<App />, { initialPath: '/scoreboard', withLeagueSeasonProvider: false });

    const moreTab = screen.getByRole('button', { name: 'More' });
    await user.click(moreTab);
    const more = screen.getByRole('dialog', { name: 'More' });
    await user.click(await within(more).findByRole('button', { name: 'My team: WOMBLE · Change' }));
    await picker();
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog', { name: 'Which team is yours?' })).toBeNull();
    expect(moreTab).toHaveFocus();
  });

  it('asks again from My team in the header, naming the saved team only to assistive tech', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const user = userEvent.setup();
    renderWithProviders(<App />, { initialPath: '/scoreboard', withLeagueSeasonProvider: false });

    const myTeam = await within(mainNavigation()).findByRole('button', { name: 'My team: WOMBLE, change' });
    expect(myTeam).toHaveTextContent(/^My team$/);
    await user.click(myTeam);

    expect(await picker()).toBeInTheDocument();
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
  });

  it('offers a choice from My team in the header to a visitor who is just browsing', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"browsing"}');
    renderWithProviders(<App />, { initialPath: '/scoreboard', withLeagueSeasonProvider: false });

    expect(await within(mainNavigation()).findByRole('button', { name: 'My team, choose' })).toBeInTheDocument();
  });

  it.each([
    ['on Admin', '/admin', ['Womble']],
    ['while the newest season has no teams', '/scoreboard', []],
  ])('offers no My team in the header %s', async (_case, path, teamNames) => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    vi.mocked(api.rosters).mockResolvedValue(teamNames.map(rosterTeam));
    renderWithProviders(<App />, { initialPath: path, withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(api.rosters).toHaveBeenCalledWith(season.id);
    });
    await act(async () => {
      await vi.mocked(api.rosters).mock.results.at(-1)?.value;
      await new Promise((resolve) => {
        setTimeout(resolve, 0);
      });
    });
    expect(within(mainNavigation()).queryByRole('button', { name: /^My team/ })).toBeNull();
  });
});
