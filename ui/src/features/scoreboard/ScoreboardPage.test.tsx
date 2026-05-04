import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ScoreboardPage from './ScoreboardPage';
import type { League, Season, Tournament, WeeklyReport } from '@/shared/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const tournamentReportMock = vi.fn();
const authMeMock = vi.fn();
const finalizeMock = vi.fn();
const competitorsMock = vi.fn();
const golfersMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: vi.fn(),
    rosters: vi.fn(),
    tournaments: (id: string) => tournamentsMock(id),
    tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
      tournamentReportMock(seasonId, tournamentId, live),
    authMe: () => authMeMock(),
    finalizeTournament: (id: string) => finalizeMock(id),
    tournamentCompetitors: (id: string) => competitorsMock(id),
    golfers: () => golfersMock(),
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
const completedTournament: Tournament = {
  id: 'tn-completed',
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
const activeTournament: Tournament = {
  ...completedTournament,
  id: 'tn-active',
  name: 'Current Open',
  status: 'in_progress',
  week: '10',
};

function buildReport(name: string, tournamentId: string): WeeklyReport {
  return {
    tournament: {
      id: tournamentId,
      name,
      startDate: '2026-03-01',
      endDate: '2026-03-04',
      status: 'completed',
      payoutMultiplier: 1,
      week: '9',
    },
    teams: [
      {
        teamId: 'team-1',
        teamName: 'Aces',
        ownerName: 'Alice',
        cells: [],
        topTenEarnings: 0,
        weeklyTotal: 0,
        previous: 0,
        subtotal: 0,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: 0,
        totalCash: 0,
      },
    ],
    undraftedTopTens: [],
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
  };
}

describe('ScoreboardPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    tournamentReportMock.mockReset();
    authMeMock.mockReset();
    finalizeMock.mockReset();
    competitorsMock.mockReset();
    golfersMock.mockReset();
    authMeMock.mockResolvedValue(null);
    competitorsMock.mockResolvedValue({
      tournamentId: 'tn-active',
      isFinalized: false,
      competitors: [],
    });
    golfersMock.mockResolvedValue([]);
  });

  it('auto-selects an active tournament over a completed one', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament, completedTournament]);
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-active' ? 'Current Open' : 'Sample Open', id)),
    );

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    expect(await screen.findByRole('heading', { name: /Current Open/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-active', true);
    });
  });

  it('defaults to the most recent tournament when every event is completed', async () => {
    const earlier = { ...completedTournament, id: 'tn-early', name: 'Early Open', week: '1' };
    const latest = { ...completedTournament, id: 'tn-latest', name: 'Latest Open', week: '20' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([earlier, latest]);
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-latest' ? 'Latest Open' : 'Early Open', id)),
    );

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    expect(await screen.findByRole('heading', { name: /Latest Open/i })).toBeInTheDocument();
    await waitFor(() => {
      // Every tournament is completed → live overlay is suppressed.
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-latest', false);
    });
  });

  it('refetches the report when the user picks a different tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament, completedTournament]);
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-active' ? 'Current Open' : 'Sample Open', id)),
    );

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Current Open/i });
    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, 'tn-completed');

    expect(await screen.findByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
    // The user switched to a completed tournament → live overlay is suppressed.
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-completed', false);
  });

  it('shows an empty state when the season has no tournaments', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });
    expect(
      await screen.findByText(/No tournaments scheduled for this season/i),
    ).toBeInTheDocument();
  });

  it('shows the Finalize Results button for an admin viewing an in-progress tournament', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open', 'tn-active'),
      tournament: {
        id: 'tn-active',
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });
    finalizeMock.mockResolvedValue({ message: 'ok' });

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    const button = await screen.findByRole('button', { name: /Finalize Results/i });
    await user.click(button);
    await waitFor(() => {
      expect(finalizeMock).toHaveBeenCalledWith('tn-active');
    });
  });

  it('hides the Finalize Results button for unauthenticated users', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open', 'tn-active'),
      tournament: {
        id: 'tn-active',
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Current Open/i });
    expect(
      screen.queryByRole('button', { name: /Finalize Results/i }),
    ).not.toBeInTheDocument();
  });

  it('hides the Finalize Results button for completed tournaments even when admin', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completedTournament]);
    tournamentReportMock.mockResolvedValue(buildReport('Sample Open', 'tn-completed'));

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Sample Open/i });
    expect(
      screen.queryByRole('button', { name: /Finalize Results/i }),
    ).not.toBeInTheDocument();
  });

  it('shows an error message when the report fails to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completedTournament]);
    tournamentReportMock.mockRejectedValue(new Error('boom'));

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });
    expect(await screen.findByText(/Failed to load scoreboard/i)).toBeInTheDocument();
  });

  it('shows the Manage player links button for an admin and opens the panel on click', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open', 'tn-active'),
      tournament: {
        id: 'tn-active',
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    const button = await screen.findByRole('button', { name: /Manage player links/i });
    await user.click(button);

    expect(await screen.findByRole('dialog', { name: /Manage player links/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(competitorsMock).toHaveBeenCalledWith('tn-active');
    });
  });

  it('hides the Manage player links button for a logged-in non-admin', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-2',
      username: 'reg',
      role: 'user',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open', 'tn-active'),
      tournament: {
        id: 'tn-active',
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });
    await screen.findByRole('heading', { name: /Current Open/i });

    expect(
      screen.queryByRole('button', { name: /Manage player links/i }),
    ).not.toBeInTheDocument();
  });
});
