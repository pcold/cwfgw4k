import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import ScoreboardPage from './ScoreboardPage';
import type { League, Season, Tournament, WeeklyReport } from '@/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const tournamentReportMock = vi.fn();

vi.mock('../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: vi.fn(),
    rosters: vi.fn(),
    tournaments: (id: string) => tournamentsMock(id),
    tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
      tournamentReportMock(seasonId, tournamentId, live),
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
        rows: [],
        topTens: 0,
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
  };
}

describe('ScoreboardPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    tournamentReportMock.mockReset();
  });

  it('auto-selects an active tournament over a completed one', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament, completedTournament]);
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-active' ? 'Current Open' : 'Sample Open', id)),
    );

    renderWithProviders(<ScoreboardPage />);

    expect(await screen.findByRole('heading', { name: /Current Open/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-active', false);
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
    renderWithProviders(<ScoreboardPage />);

    await screen.findByRole('heading', { name: /Current Open/i });
    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, 'tn-completed');

    expect(await screen.findByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-completed', false);
  });

  it('shows an empty state when the season has no tournaments', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<ScoreboardPage />);
    expect(
      await screen.findByText(/No tournaments scheduled for this season/i),
    ).toBeInTheDocument();
  });

  it('shows an error message when the report fails to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completedTournament]);
    tournamentReportMock.mockRejectedValue(new Error('boom'));

    renderWithProviders(<ScoreboardPage />);
    expect(await screen.findByText(/Failed to load scoreboard/i)).toBeInTheDocument();
  });
});
