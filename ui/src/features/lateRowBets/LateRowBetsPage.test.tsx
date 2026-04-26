import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import LateRowBetsPage from './LateRowBetsPage';
import type { League, Season, Tournament, WeeklyReport } from '@/shared/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const seasonReportMock = vi.fn();
const tournamentReportMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: (id: string, live: boolean) => seasonReportMock(id, live),
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
const tournament: Tournament = {
  id: 'tn-1',
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

function buildReport(): WeeklyReport {
  return {
    tournament: {
      id: null,
      name: null,
      startDate: null,
      endDate: null,
      status: 'season',
      payoutMultiplier: 1,
      week: null,
    },
    teams: [
      {
        teamId: 't-1',
        teamName: 'Aces',
        ownerName: 'Alice',
        rows: [],
        topTenEarnings: 0,
        weeklyTotal: 0,
        previous: 0,
        subtotal: 0,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: 45,
        totalCash: 100,
      },
    ],
    undraftedTopTens: [],
    sideBetDetail: [
      {
        round: 5,
        teams: [
          { teamId: 't-1', golferName: 'Scottie', cumulativeEarnings: 500, payout: 45 },
        ],
      },
    ],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
  };
}

describe('LateRowBetsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    seasonReportMock.mockReset();
    tournamentReportMock.mockReset();
  });

  it('loads the season report by default (All Tournaments) when the season has no tournaments yet', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    seasonReportMock.mockResolvedValue(buildReport());

    renderWithProviders(<LateRowBetsPage />);

    expect(
      await screen.findByRole('heading', { name: /Round 5-8 Late Row Bets/i }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Round 5' })).toBeInTheDocument();
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1', true);
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('switches to the per-tournament report when a tournament is selected', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    seasonReportMock.mockResolvedValue(buildReport());
    tournamentReportMock.mockResolvedValue(buildReport());

    const user = userEvent.setup();
    renderWithProviders(<LateRowBetsPage />);

    await screen.findByRole('heading', { name: 'Round 5' });
    const select = screen.getByLabelText(/Through/i);
    await user.selectOptions(select, 'tn-1');

    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true);
  });

  it('shows an empty state when there are no leagues', async () => {
    leaguesMock.mockResolvedValue([]);
    renderWithProviders(<LateRowBetsPage />);
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
  });

  it('surfaces an error when the leagues query fails', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<LateRowBetsPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });

  it('surfaces an error when the report query fails', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    seasonReportMock.mockRejectedValue(new Error('kaboom'));

    renderWithProviders(<LateRowBetsPage />);
    expect(await screen.findByText(/Failed to load report/i)).toBeInTheDocument();
  });
});
