import { describe, expect, it } from 'vitest';
import type { ReportSideBetTeamEntry, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import {
  lateRowBetRounds,
  lateRowBetTeamTotals,
  summarizeLateRowBets,
} from './lateRowBetsModel';

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
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

function entry(overrides: Partial<ReportSideBetTeamEntry> = {}): ReportSideBetTeamEntry {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    golferName: 'Golfer',
    cumulativeEarnings: 0,
    payout: 0,
    outcome: null,
    ...overrides,
  };
}

function baseReport(): WeeklyReport {
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
    teams: [],
    undraftedTopTens: [],
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
  };
}

describe('lateRowBetRounds', () => {
  it('returns an empty array when there is no side bet detail', () => {
    expect(lateRowBetRounds(baseReport())).toEqual([]);
  });

  it('sorts entries by cumulative earnings descending', () => {
    const report: WeeklyReport = {
      ...baseReport(),
      sideBetRules: { amountPerTeam: 20, rounds: [2] },
      sideBetDetail: [
        {
          round: 2,
          teams: [
            entry({ teamId: 't-2', teamName: 'Birdies', cumulativeEarnings: 50, payout: -20 }),
            entry({ teamId: 't-1', teamName: 'Aces', cumulativeEarnings: 200, payout: 40 }),
            entry({ teamId: 't-3', teamName: 'Eagles', cumulativeEarnings: 100, payout: -20 }),
          ],
        },
      ],
    };

    const rounds = lateRowBetRounds(report);
    expect(rounds).toHaveLength(1);
    expect(rounds[0].round).toBe(2);
    expect(rounds[0].entries.map((e) => e.teamName)).toEqual(['Aces', 'Eagles', 'Birdies']);
    expect(rounds[0].entries[0]).toMatchObject({
      teamId: 't-1',
      golferName: 'Golfer',
      cumulativeEarnings: 200,
      payout: 40,
    });
  });

  it("takes each entry's team name from the entry, whatever the teams list calls that team", () => {
    const report: WeeklyReport = {
      ...baseReport(),
      teams: [team({ teamId: 't-1', teamName: 'Renamed Since' })],
      sideBetRules: { amountPerTeam: 20, rounds: [3] },
      sideBetDetail: [{ round: 3, teams: [entry({ teamId: 't-1', teamName: 'Aces' })] }],
    };
    expect(lateRowBetRounds(report)[0].entries[0].teamName).toBe('Aces');
  });
});

describe('lateRowBetTeamTotals', () => {
  it('sorts teams by sideBets descending', () => {
    const report: WeeklyReport = {
      ...baseReport(),
      teams: [
        team({ teamId: 't-1', teamName: 'Aces', sideBets: -30 }),
        team({ teamId: 't-2', teamName: 'Birdies', sideBets: 60 }),
        team({ teamId: 't-3', teamName: 'Eagles', sideBets: 0 }),
      ],
    };
    expect(lateRowBetTeamTotals(report).map((t) => t.teamName)).toEqual([
      'Birdies',
      'Eagles',
      'Aces',
    ]);
  });
});

describe('summarizeLateRowBets', () => {
  it('returns zeros when there are no teams', () => {
    expect(summarizeLateRowBets(baseReport())).toEqual({ totalWon: 0, totalLost: 0, net: 0 });
  });

  it('splits wins and losses on sideBets (zero-sum by design)', () => {
    const report: WeeklyReport = {
      ...baseReport(),
      teams: [
        team({ teamId: 't-1', sideBets: 60 }),
        team({ teamId: 't-2', sideBets: 45 }),
        team({ teamId: 't-3', sideBets: -45 }),
        team({ teamId: 't-4', sideBets: -60 }),
      ],
    };
    expect(summarizeLateRowBets(report)).toEqual({
      totalWon: 105,
      totalLost: -105,
      net: 0,
    });
  });
});
