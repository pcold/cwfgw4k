import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import type { League, Season, Tournament, WeeklyReport } from '@/shared/api/types';
import LateRowBetsPage from './LateRowBetsPage';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const tournamentsMock = vi.fn<typeof api.tournaments>();
const seasonReportMock = vi.fn<typeof api.seasonReport>();
const tournamentReportMock = vi.fn<typeof api.tournamentReport>();

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

function buildReport(): WeeklyReport {
  return {
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
    teams: [
      {
        teamId: 't-1',
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
        totalCash: 100,
      },
    ],
    undraftedTopTens: [],
    sideBetRules: { amountPerTeam: 20, rounds: [2, 3] },
    sideBetDetail: [2, 3].map((round) => ({
      round,
      teams: [
        {
          teamId: 't-1',
          teamName: 'Aces',
          golferName: 'Scottie',
          cumulativeEarnings: 500,
          payout: 0,
          outcome: 'won',
        },
      ],
    })),
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
  };
}

// The body a report cached before the server sent rules, team names and outcomes still serves.
function buildReportWithoutSideBetFacts(): unknown {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- destructured only to omit it from the stale body.
  const { sideBetRules: _rules, sideBetDetail, ...rest } = buildReport();
  return {
    ...rest,
    sideBetDetail: sideBetDetail.map(({ round, teams }) => ({
      round,
      teams: teams.map(({ teamId, golferName, cumulativeEarnings, payout }) => ({
        teamId,
        golferName,
        cumulativeEarnings,
        payout,
      })),
    })),
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

    expect(await screen.findByRole('heading', { name: 'Late Row Bets' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1', true);
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it("shows the season's own side-bet amount and rounds, not the defaults", async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    seasonReportMock.mockResolvedValue(buildReport());

    renderWithProviders(<LateRowBetsPage />);

    expect(
      await screen.findByText(/^\$20\.00 per team in each of draft rounds 2 and 3\./),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Round 3' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Round 5' })).not.toBeInTheDocument();
  });

  it('renders the rounds of a report cached before the rules were sent, without a rules sentence', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    // @ts-expect-error -- a body cached before the server sent these fields no longer satisfies WeeklyReport.
    seasonReportMock.mockResolvedValue(buildReportWithoutSideBetFacts());

    renderWithProviders(<LateRowBetsPage />);

    expect(await screen.findByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(screen.getAllByText('Scottie')).toHaveLength(2);
    expect(screen.queryByText(/per team in/)).not.toBeInTheDocument();
  });

  it('switches to the per-tournament report when a tournament is selected', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    seasonReportMock.mockResolvedValue(buildReport());
    tournamentReportMock.mockResolvedValue(buildReport());

    const user = userEvent.setup();
    renderWithProviders(<LateRowBetsPage />);

    await screen.findByRole('heading', { name: 'Round 2' });
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
