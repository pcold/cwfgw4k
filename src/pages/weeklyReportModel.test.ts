import { describe, expect, it } from 'vitest';
import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/api/types';
import {
  isSeasonReport,
  roundCellBg,
  roundTextColor,
  sideBetWinnersByRound,
  signTextClass,
  summarizeWeeklyReport,
  teamRowsByRound,
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
    liveLeaderboard: [],
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
        topTenEarnings: 0,
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
        topTenEarnings: 0,
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

function reportRow(round: number): ReportRow {
  return {
    round,
    golferName: `Golfer ${round}`,
    golferId: `g-${round}`,
    positionStr: 'T1',
    scoreToPar: '-10',
    earnings: 0,
    topTens: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
  };
}

describe('teamRowsByRound', () => {
  it('indexes a team\u2019s rows by round number', () => {
    const team: ReportTeamColumn = {
      teamId: 't-1',
      teamName: 'Aces',
      ownerName: 'A',
      rows: [reportRow(2), reportRow(5)],
      topTenEarnings: 0,
      weeklyTotal: 0,
      previous: 0,
      subtotal: 0,
      topTenCount: 0,
      topTenMoney: 0,
      sideBets: 0,
      totalCash: 0,
    };
    const map = teamRowsByRound(team);
    expect(map.get(2)?.golferName).toBe('Golfer 2');
    expect(map.get(5)?.golferName).toBe('Golfer 5');
    expect(map.has(1)).toBe(false);
  });
});

describe('signTextClass', () => {
  it('returns the green class for positive amounts', () => {
    expect(signTextClass(100)).toBe('text-green-400');
  });
  it('returns the red class for negative amounts', () => {
    expect(signTextClass(-1)).toBe('text-red-400');
  });
  it('returns the muted class for zero', () => {
    expect(signTextClass(0)).toBe('text-gray-500');
  });
});

describe('roundCellBg', () => {
  it('prefers the side-bet winner background over earnings', () => {
    expect(roundCellBg(true, true)).toBe('bg-red-800');
  });
  it('uses the earnings background when only earnings are present', () => {
    expect(roundCellBg(true, false)).toBe('bg-yellow-300');
  });
  it('returns no background when neither applies', () => {
    expect(roundCellBg(false, false)).toBe('');
  });
});

describe('roundTextColor', () => {
  it('uses dark text only when earnings are present without a side-bet win', () => {
    expect(roundTextColor(true, false)).toBe('text-black');
    expect(roundTextColor(true, true)).toBe('text-gray-200');
    expect(roundTextColor(false, false)).toBe('text-gray-200');
  });
});
