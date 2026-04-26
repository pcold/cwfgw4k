import { describe, expect, it } from 'vitest';
import type { ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
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

function baseReport(): WeeklyReport {
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
    teams: [],
    undraftedTopTens: [],
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

  it('resolves team names and sorts entries by cumulative earnings descending', () => {
    const report: WeeklyReport = {
      ...baseReport(),
      teams: [
        team({ teamId: 't-1', teamName: 'Aces' }),
        team({ teamId: 't-2', teamName: 'Birdies' }),
        team({ teamId: 't-3', teamName: 'Eagles' }),
      ],
      sideBetDetail: [
        {
          round: 5,
          teams: [
            { teamId: 't-2', golferName: 'B', cumulativeEarnings: 50, payout: -15 },
            { teamId: 't-1', golferName: 'A', cumulativeEarnings: 200, payout: 30 },
            { teamId: 't-3', golferName: 'C', cumulativeEarnings: 100, payout: -15 },
          ],
        },
      ],
    };

    const rounds = lateRowBetRounds(report);
    expect(rounds).toHaveLength(1);
    expect(rounds[0].round).toBe(5);
    expect(rounds[0].entries.map((e) => e.teamName)).toEqual(['Aces', 'Eagles', 'Birdies']);
    expect(rounds[0].entries[0]).toMatchObject({
      teamId: 't-1',
      golferName: 'A',
      cumulativeEarnings: 200,
      payout: 30,
    });
  });

  it('falls back to "?" when a side-bet team id is not in the teams list', () => {
    const report: WeeklyReport = {
      ...baseReport(),
      teams: [team({ teamId: 't-1', teamName: 'Aces' })],
      sideBetDetail: [
        {
          round: 6,
          teams: [
            { teamId: 't-ghost', golferName: 'X', cumulativeEarnings: 0, payout: 0 },
          ],
        },
      ],
    };
    expect(lateRowBetRounds(report)[0].entries[0].teamName).toBe('?');
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
