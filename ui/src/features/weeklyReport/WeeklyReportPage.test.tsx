import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import WeeklyReportPage from './WeeklyReportPage';
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
  tieFloor: 1,
  sideBetAmount: 15,
  maxTeams: 13,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
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

function buildReport(name: string, tournamentId: string | null = null): WeeklyReport {
  return {
    tournament: {
      id: tournamentId,
      name,
      startDate: tournamentId ? '2026-03-01' : null,
      endDate: tournamentId ? '2026-03-04' : null,
      status: tournamentId ? 'completed' : 'season',
      payoutMultiplier: 1,
      week: tournamentId ? '9' : null,
    },
    teams: [
      {
        teamId: 'team-1',
        teamName: 'Aces',
        ownerName: 'Alice',
        cells: [1, 2, 3, 4, 5, 6, 7, 8].map((r) => ({
          round: r,
          golferName: `Golfer ${r}`,
          golferId: `g-${r}`,
          positionStr: null,
          scoreToPar: null,
          earnings: 0,
          topTens: 0,
          ownershipPct: 100,
          seasonEarnings: 0,
          seasonTopTens: 0,
          pairKey: null,
        })),
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

describe('WeeklyReportPage', () => {
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
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /All Tournaments/i })).toBeInTheDocument();
    // All Tournaments view never triggers live overlay.
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1', false);
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('defaults to the earliest unfinalized tournament and switches to the season report when All Tournaments is selected', async () => {
    const inProgress = { ...tournament, status: 'in_progress' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([inProgress]);
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));
    tournamentReportMock.mockResolvedValue(buildReport('Sample Open', 'tn-1'));

    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
    // tn-1 is the earliest non-finalized tournament → live overlay applies.
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true);

    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, '');

    expect(await screen.findByRole('heading', { name: /All Tournaments/i })).toBeInTheDocument();
    // Switching to All Tournaments suppresses the live overlay.
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1', false);
  });

  it('defaults to All Tournaments when every tournament is already completed', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /All Tournaments/i })).toBeInTheDocument();
    // All Tournaments view never triggers live overlay.
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1', false);
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('shows an empty state when there are no leagues', async () => {
    leaguesMock.mockResolvedValue([]);
    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
  });

  it('surfaces an error when the leagues query fails', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });

  it('surfaces an error when the report query fails', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    seasonReportMock.mockRejectedValue(new Error('kaboom'));

    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/Failed to load report/i)).toBeInTheDocument();
  });

  it('enables the Download PDF button once the report loads', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    const button = await screen.findByRole('button', { name: /Download PDF/i });
    await screen.findByRole('heading', { name: /All Tournaments/i });
    expect(button).toBeEnabled();
  });

  it('leaves the Download PDF button disabled while the report is loading', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    seasonReportMock.mockImplementation(() => new Promise(() => {}));

    renderWithProviders(<WeeklyReportPage />);

    const button = await screen.findByRole('button', { name: /Download PDF/i });
    expect(button).toBeDisabled();
  });
});
