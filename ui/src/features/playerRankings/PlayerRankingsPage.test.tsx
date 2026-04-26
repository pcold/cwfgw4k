import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PlayerRankingsPage from './PlayerRankingsPage';
import type {
  League,
  ReportCell,
  ReportTeamColumn,
  RosterTeam,
  Season,
  Tournament,
  WeeklyReport,
} from '@/shared/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const rostersMock = vi.fn();
const tournamentReportMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    tournaments: (id: string) => tournamentsMock(id),
    rosters: (id: string) => rostersMock(id),
    tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
      tournamentReportMock(seasonId, tournamentId, live),
    seasonReport: vi.fn(),
    rankings: vi.fn(),
    golferHistory: vi.fn(),
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

function tournament(overrides: Partial<Tournament>): Tournament {
  return {
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
    ...overrides,
  };
}

function row(overrides: Partial<ReportCell>): ReportCell {
  return {
    round: 1,
    golferName: 'SCHEFFLER',
    golferId: 'g-1',
    positionStr: 'T1',
    scoreToPar: '-12',
    earnings: 0,
    topTens: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
    pairKey: null,
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn>): ReportTeamColumn {
  return {
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
    totalCash: 0,
    ...overrides,
  };
}

function report(tournamentId: string, overrides: Partial<WeeklyReport>): WeeklyReport {
  return {
    tournament: {
      id: tournamentId,
      name: 'Open',
      startDate: '2026-03-01',
      endDate: '2026-03-04',
      status: 'completed',
      payoutMultiplier: 1,
      week: '9',
    },
    teams: [],
    undraftedTopTens: [],
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    ...overrides,
  };
}

const rosters: RosterTeam[] = [
  {
    teamId: 't-1',
    teamName: 'Aces',
    picks: [
      { round: 1, golferName: 'Scottie Scheffler', ownershipPct: 100, golferId: 'g-1' },
    ],
  },
];

describe('PlayerRankingsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    rostersMock.mockReset();
    tournamentReportMock.mockReset();
  });

  it('aggregates drafted and undrafted top tens across all completed tournaments', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A', startDate: '2026-03-01' });
    const tournB = tournament({ id: 'tn-b', name: 'Open B', startDate: '2026-03-15' });

    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA, tournB]);
    rostersMock.mockResolvedValue(rosters);

    tournamentReportMock.mockImplementation((_s: string, tournamentId: string) => {
      if (tournamentId === 'tn-a') {
        return Promise.resolve(
          report('tn-a', {
            teams: [team({ cells: [row({ golferId: 'g-1', earnings: 18, topTens: 1 })] })],
            undraftedTopTens: [
              { name: 'P. Mickelson', position: 5, payout: 8, scoreToPar: '-3', pairKey: null },
            ],
          }),
        );
      }
      return Promise.resolve(
        report('tn-b', {
          teams: [team({ cells: [row({ golferId: 'g-1', earnings: 12, topTens: 1 })] })],
          undraftedTopTens: [],
        }),
      );
    });

    renderWithProviders(<PlayerRankingsPage />);

    expect(
      await screen.findByRole('heading', { name: /Player Rankings/i }),
    ).toBeInTheDocument();
    const rows = await screen.findAllByRole('row');
    const dataRows = rows.slice(1);
    expect(dataRows).toHaveLength(2);

    const scheffler = within(dataRows[0]).getAllByRole('cell');
    expect(scheffler[1]).toHaveTextContent('Scottie Scheffler');
    expect(scheffler[2]).toHaveTextContent('2');
    expect(scheffler[3]).toHaveTextContent('$30');
    expect(scheffler[4]).toHaveTextContent('Aces');
    expect(scheffler[5]).toHaveTextContent('1');

    const phil = within(dataRows[1]).getAllByRole('cell');
    expect(phil[1]).toHaveTextContent('P. Mickelson');
    expect(phil[4]).toHaveTextContent('undrafted');
  });

  it('limits the aggregation when a "through" tournament is selected', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A', startDate: '2026-03-01' });
    const tournB = tournament({ id: 'tn-b', name: 'Open B', startDate: '2026-03-15' });

    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA, tournB]);
    rostersMock.mockResolvedValue(rosters);
    tournamentReportMock.mockImplementation((_s: string, tournamentId: string) => {
      const earnings = tournamentId === 'tn-a' ? 18 : 12;
      return Promise.resolve(
        report(tournamentId, {
          teams: [team({ cells: [row({ golferId: 'g-1', earnings, topTens: 1 })] })],
        }),
      );
    });

    const user = userEvent.setup();
    renderWithProviders(<PlayerRankingsPage />);
    await screen.findByText('Scottie Scheffler');
    // All Tournaments: $18 + $12 = $30
    expect(within(screen.getAllByRole('row')[1]).getAllByRole('cell')[3]).toHaveTextContent(
      '$30.00',
    );

    const select = screen.getByLabelText(/Through/i);
    await user.selectOptions(select, 'tn-a');

    // Through tn-a only: $18
    await screen.findByText('$18.00');
    expect(within(screen.getAllByRole('row')[1]).getAllByRole('cell')[3]).toHaveTextContent(
      '$18.00',
    );
  });

  it('shows an empty state when no players have a top 10', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A' });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA]);
    rostersMock.mockResolvedValue(rosters);
    tournamentReportMock.mockResolvedValue(report('tn-a', {}));

    renderWithProviders(<PlayerRankingsPage />);
    expect(
      await screen.findByText(/No players with a top 10 finish yet/i),
    ).toBeInTheDocument();
  });

  it('surfaces an error when a report query fails', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A' });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA]);
    rostersMock.mockResolvedValue(rosters);
    tournamentReportMock.mockRejectedValue(new Error('boom'));

    renderWithProviders(<PlayerRankingsPage />);
    expect(await screen.findByText(/Failed to load reports/i)).toBeInTheDocument();
  });
});
