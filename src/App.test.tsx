import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { renderWithProviders } from './test/renderWithProviders';

vi.mock('./api/client', () => ({
  api: {
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
  it('renders the primary nav', () => {
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });
    expect(screen.getByRole('link', { name: /Scoreboard/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Weekly Report/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Team Standings/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Rosters/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Late Row Bets/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Rules/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'CWFG' })).toBeInTheDocument();
  });

  it('navigates to the Rankings page when the nav link is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { withLeagueSeasonProvider: false });

    await user.click(screen.getByRole('link', { name: /Team Standings/i }));
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
  });
});
