import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/shared/api/client';
import type { League, Season } from '@/shared/api/types';
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

describe('App', () => {
  // The mobile menu mounts inside the navigation landmark only once opened, so a query within
  // that landmark before the hamburger is clicked sees the desktop links alone.

  it('renders the site heading', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(screen.getByRole('heading', { name: 'Castlewood FG' })).toBeInTheDocument();
  });

  it("marks the current page's nav link, and only that one, as the current page", () => {
    renderWithProviders(<App />, { initialPath: '/rankings', withLeagueSeasonProvider: false });

    const currentLinks = within(screen.getByRole('navigation'))
      .getAllByRole('link')
      .filter((link) => link.hasAttribute('aria-current'));
    expect(currentLinks.map((link) => [link.textContent, link.getAttribute('aria-current')])).toEqual([
      ['Standings', 'page'],
    ]);
  });

  it('gives every desktop nav link the tap-target height, its text centred in it', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    for (const link of within(screen.getByRole('navigation')).getAllByRole('link')) {
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

    const navigation = screen.getByRole('navigation');
    expect(within(navigation).getByRole('link', { name: label })).toHaveAttribute('href', path);
  });

  it('toggles the mobile hamburger menu and lets the user follow a link', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    // Mobile nav container is absent until the hamburger is opened.
    expect(container.querySelector('#mobile-nav')).toBeNull();

    await user.click(screen.getByRole('button', { name: /Open navigation menu/i }));
    const mobileNav = container.querySelector('#mobile-nav') as HTMLElement;
    expect(mobileNav).not.toBeNull();

    await user.click(within(mobileNav).getByRole('link', { name: 'Standings' }));
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
    // Closes itself after navigation
    expect(container.querySelector('#mobile-nav')).toBeNull();
  });

  it('keeps Admin out of the main nav and the mobile menu', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(within(screen.getByRole('navigation')).queryByRole('link', { name: /admin/i })).toBeNull();

    await user.click(screen.getByRole('button', { name: /Open navigation menu/i }));
    const mobileNav = container.querySelector('#mobile-nav') as HTMLElement;
    expect(within(mobileNav).getByRole('link', { name: 'Rules' })).toBeInTheDocument();
    expect(within(mobileNav).queryByRole('link', { name: /admin/i })).toBeNull();
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

  it("puts the league and season picker in the page's title row, and nowhere else", async () => {
    vi.mocked(api.leagues).mockResolvedValueOnce([league]);
    vi.mocked(api.seasons).mockResolvedValueOnce([season]);
    renderWithProviders(<App />, { initialPath: '/rosters', withLeagueSeasonProvider: false });

    const leagueSelect = await screen.findByRole('combobox', { name: 'League' });
    const titleRow = pageTitleRow(screen.getByRole('heading', { name: 'Team Rosters' }));
    expect(titleRow).toContainElement(leagueSelect);
    expect(within(titleRow).getByRole('combobox', { name: 'Season' })).toBeInTheDocument();
    expect(screen.getAllByRole('combobox', { name: /^(League|Season)$/ })).toHaveLength(2);
  });
});
