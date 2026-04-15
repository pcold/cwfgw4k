import { describe, expect, it } from 'vitest';
import { buildPlayerRankings } from './playerRankingsModel';
import type { ReportRow, ReportTeamColumn, RosterTeam, WeeklyReport } from '@/api/types';

function row(overrides: Partial<ReportRow> = {}): ReportRow {
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
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
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
    sideBets: 0,
    totalCash: 0,
    ...overrides,
  };
}

function report(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
  return {
    tournament: {
      id: 't-1',
      name: 'Sample Open',
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

const aces: RosterTeam = {
  teamId: 't-1',
  teamName: 'Aces',
  picks: [
    { round: 1, golferName: 'Scottie Scheffler', ownershipPct: 100, golferId: 'g-1' },
    { round: 3, golferName: 'Rory McIlroy', ownershipPct: 100, golferId: 'g-2' },
  ],
};

describe('buildPlayerRankings', () => {
  it('returns an empty list when there are no top tens', () => {
    const result = buildPlayerRankings([report()], [aces]);
    expect(result).toEqual([]);
  });

  it('aggregates a drafted golfer across multiple tournaments', () => {
    const reports = [
      report({
        teams: [
          team({ rows: [row({ golferId: 'g-1', earnings: 18, topTens: 1 })] }),
        ],
      }),
      report({
        teams: [
          team({ rows: [row({ golferId: 'g-1', earnings: 12, topTens: 1 })] }),
        ],
      }),
    ];
    const result = buildPlayerRankings(reports, [aces]);
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      golferId: 'g-1',
      name: 'Scottie Scheffler',
      topTens: 2,
      totalEarnings: 30,
      teamName: 'Aces',
      draftRound: 1,
    });
  });

  it('skips rows that did not finish in the top ten', () => {
    const reports = [
      report({
        teams: [
          team({
            rows: [
              row({ golferId: 'g-1', earnings: 18, topTens: 1 }),
              row({ golferId: 'g-2', earnings: 0, topTens: 0 }),
            ],
          }),
        ],
      }),
    ];
    const result = buildPlayerRankings(reports, [aces]);
    expect(result.map((r) => r.golferId)).toEqual(['g-1']);
  });

  it('aggregates undrafted golfers by name', () => {
    const reports = [
      report({
        undraftedTopTens: [
          { name: 'P. Mickelson', position: 5, payout: 8, scoreToPar: '-3' },
        ],
      }),
      report({
        undraftedTopTens: [
          { name: 'P. Mickelson', position: 9, payout: 4, scoreToPar: '-1' },
        ],
      }),
    ];
    const result = buildPlayerRankings(reports, []);
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      golferId: null,
      name: 'P. Mickelson',
      topTens: 2,
      totalEarnings: 12,
      teamName: null,
      draftRound: null,
    });
  });

  it('sorts by total earnings descending, then top tens, then name', () => {
    const reports = [
      report({
        teams: [
          team({
            rows: [
              row({ golferId: 'g-1', earnings: 30, topTens: 1 }),
              row({ golferId: 'g-2', earnings: 50, topTens: 1 }),
            ],
          }),
        ],
        undraftedTopTens: [
          { name: 'A. Player', position: 8, payout: 30, scoreToPar: '0' },
        ],
      }),
    ];
    const result = buildPlayerRankings(reports, [aces]);
    expect(result.map((r) => r.name)).toEqual([
      'Rory McIlroy',
      'A. Player',
      'Scottie Scheffler',
    ]);
  });

  it('falls back to the report row name when the golfer is not on a roster', () => {
    const reports = [
      report({
        teams: [
          team({
            rows: [
              row({ golferId: 'g-orphan', golferName: 'ORPHAN', earnings: 18, topTens: 1 }),
            ],
          }),
        ],
      }),
    ];
    const result = buildPlayerRankings(reports, []);
    expect(result[0]).toMatchObject({
      name: 'ORPHAN',
      teamName: null,
      draftRound: null,
    });
  });

  it('uses the full name from rosters even when the report sends a last-name', () => {
    const reports = [
      report({
        teams: [
          team({
            rows: [
              row({ golferId: 'g-1', golferName: 'SCHEFFLER', earnings: 18, topTens: 1 }),
            ],
          }),
        ],
      }),
    ];
    const result = buildPlayerRankings(reports, [aces]);
    expect(result[0].name).toBe('Scottie Scheffler');
  });
});
