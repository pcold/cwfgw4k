import { describe, expect, it } from 'vitest';
import type { WeeklyReport } from '@/api/types';
import {
  isSeasonReport,
  sideBetWinnersByRound,
  summarizeWeeklyReport,
} from './weeklyReportModel';

function tournamentReport(status: string): WeeklyReport {
  return {
    tournament: {
      id: null,
      name: null,
      startDate: null,
      endDate: null,
      status,
      payoutMultiplier: 1,
      week: null,
    },
    teams: [],
    undraftedTopTens: [],
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
  };
}

describe('sideBetWinnersByRound', () => {
  it('returns an empty map when there is no side bet detail', () => {
    expect(sideBetWinnersByRound([]).size).toBe(0);
  });

  it('includes only teams with a positive payout for the round', () => {
    const map = sideBetWinnersByRound([
      {
        round: 5,
        teams: [
          { teamId: 't-1', golferName: 'A', cumulativeEarnings: 100, payout: 45 },
          { teamId: 't-2', golferName: 'B', cumulativeEarnings: 50, payout: -15 },
          { teamId: 't-3', golferName: 'C', cumulativeEarnings: 0, payout: 0 },
        ],
      },
      {
        round: 6,
        teams: [
          { teamId: 't-2', golferName: 'D', cumulativeEarnings: 200, payout: 60 },
        ],
      },
    ]);
    expect(map.get(5)).toEqual(new Set(['t-1']));
    expect(map.get(6)).toEqual(new Set(['t-2']));
    expect(map.has(7)).toBe(false);
  });
});

describe('summarizeWeeklyReport', () => {
  it('returns zeros when there are no teams', () => {
    const summary = summarizeWeeklyReport(tournamentReport('completed'));
    expect(summary).toEqual({ totalWon: 0, totalLost: 0, net: 0 });
  });

  it('sums positive totalCash as won and negative as lost (zero-sum by design)', () => {
    const report = tournamentReport('completed');
    report.teams = [
      {
        teamId: 't-1',
        teamName: 'Aces',
        ownerName: 'A',
        rows: [],
        topTens: 0,
        weeklyTotal: 0,
        previous: 0,
        subtotal: 0,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: 0,
        totalCash: 120,
      },
      {
        teamId: 't-2',
        teamName: 'Birdies',
        ownerName: 'B',
        rows: [],
        topTens: 0,
        weeklyTotal: 0,
        previous: 0,
        subtotal: 0,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: 0,
        totalCash: -80,
      },
    ];
    expect(summarizeWeeklyReport(report)).toEqual({
      totalWon: 120,
      totalLost: -80,
      net: 40,
    });
  });
});

describe('isSeasonReport', () => {
  it('returns true when tournament status is "season"', () => {
    expect(isSeasonReport(tournamentReport('season'))).toBe(true);
  });
  it('returns false for ordinary tournament statuses', () => {
    expect(isSeasonReport(tournamentReport('completed'))).toBe(false);
    expect(isSeasonReport(tournamentReport('in_progress'))).toBe(false);
  });
});
