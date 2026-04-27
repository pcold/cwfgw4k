import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

vi.mock('@/shared/api/client', () => ({
  api: {
    authMe: vi.fn().mockResolvedValue(null),
    leagues: vi.fn().mockResolvedValue([]),
    seasons: vi.fn().mockResolvedValue([]),
    seasonReport: vi.fn().mockResolvedValue({
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
      sideBetDetail: [],
      standingsOrder: [],
      live: false,
      liveLeaderboard: [],
    }),
    rankings: vi.fn().mockResolvedValue({
      teams: [],
      weeks: [],
      tournamentNames: [],
      live: false,
    }),
    rosters: vi.fn().mockResolvedValue([]),
    tournaments: vi.fn().mockResolvedValue([]),
    tournamentReport: vi.fn().mockResolvedValue(null),
  },
  ApiError: class ApiError extends Error {},
}));

describe('App', () => {
  // Both the desktop nav and (when open) the mobile hamburger nav end up in the DOM,
  // so queries use getAllByRole / the mobile container when a single hit is needed.

  it('renders all primary nav links', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    expect(screen.getByRole('heading', { name: 'CWFG' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /Scoreboard/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Weekly Report/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Team Standings/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Player Rankings/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Rosters/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Late Row Bets/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Rules/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /Admin/i }).length).toBeGreaterThan(0);
  });

  it('toggles the mobile hamburger menu and lets the user follow a link', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    // Mobile nav container is absent until the hamburger is opened.
    expect(container.querySelector('#mobile-nav')).toBeNull();

    await user.click(screen.getByRole('button', { name: /Open navigation menu/i }));
    const mobileNav = container.querySelector('#mobile-nav') as HTMLElement;
    expect(mobileNav).not.toBeNull();

    await user.click(within(mobileNav).getByRole('link', { name: /Team Standings/i }));
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
    // Closes itself after navigation
    expect(container.querySelector('#mobile-nav')).toBeNull();
  });
});
