import { describe, expect, it } from 'vitest';
import type {
  ReportCell,
  ReportScope,
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  SideBetOutcome,
  WeeklyReport,
} from '@/shared/api/types';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import {
  isSeasonReport,
  roundCellBg,
  roundTextColor,
  sideBetAmountByRound,
  sideBetRowLabel,
  sideBetWinnersByRound,
  teamCellsByRound,
} from './weeklyReportModel';

function reportScoped(scope: ReportScope, status: string | null = null): WeeklyReport {
  return {
    scope,
    tournament: {
      name: null,
      startDate: null,
      endDate: null,
      status,
      payoutMultiplier: 1,
      week: null,
    },
    teams: [],
    undraftedTopTens: [],
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveEligible: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
  };
}

function sideBetEntry(
  teamId: string,
  payout: number,
  outcome: SideBetOutcome | null,
): ReportSideBetTeamEntry {
  return { teamId, teamName: teamId, golferName: 'Golfer', cumulativeEarnings: 0, payout, outcome };
}

describe('sideBetWinnersByRound', () => {
  it('returns an empty map when there is no side bet detail', () => {
    expect(sideBetWinnersByRound([]).size).toBe(0);
  });

  it('includes only the teams the server marked as winners of the round', () => {
    const map = sideBetWinnersByRound([
      {
        round: 2,
        teams: [sideBetEntry('t-1', 40, 'won'), sideBetEntry('t-2', -20, 'lost')],
      },
      { round: 3, teams: [sideBetEntry('t-2', 20, 'won')] },
    ]);
    expect(map.get(2)).toEqual(new Set(['t-1']));
    expect(map.get(3)).toEqual(new Set(['t-2']));
    expect(map.has(4)).toBe(false);
  });

  it('counts every team as a winner of a round they all tie, where nobody is paid', () => {
    const map = sideBetWinnersByRound([
      { round: 2, teams: [sideBetEntry('t-1', 0, 'won'), sideBetEntry('t-2', 0, 'won')] },
    ]);
    expect(map.get(2)).toEqual(new Set(['t-1', 't-2']));
  });

  it('names no winner of a round the server has not settled, whatever the payouts say', () => {
    const map = sideBetWinnersByRound([
      { round: 2, teams: [sideBetEntry('t-1', 40, null), sideBetEntry('t-2', -20, null)] },
    ]);
    expect(map.get(2)).toEqual(new Set());
  });
});

describe('sideBetAmountByRound', () => {
  it("maps each of the season's side-bet rounds to the season's amount", () => {
    const amounts = sideBetAmountByRound({ amountPerTeam: 20, rounds: [2, 3] });
    expect([...amounts]).toEqual([
      [2, 20],
      [3, 20],
    ]);
  });

  it('is empty for a season that stakes nothing on side bets', () => {
    expect(sideBetAmountByRound(null).size).toBe(0);
  });
});

describe('sideBetRowLabel', () => {
  it("names the season's side-bet rounds", () => {
    expect(sideBetRowLabel({ amountPerTeam: 20, rounds: [2, 3] })).toBe('ROWS 2-3');
  });

  it('names a single side-bet round in the singular', () => {
    expect(sideBetRowLabel({ amountPerTeam: 20, rounds: [7] })).toBe('ROW 7');
  });

  it('falls back to a label that names no rounds when the season has none', () => {
    expect(sideBetRowLabel(null)).toBe('SIDE BETS');
  });
});

describe('isSeasonReport', () => {
  it('returns true for a season-scoped report', () => {
    expect(isSeasonReport(reportScoped('season'))).toBe(true);
  });
  it('returns false for a tournament-scoped report whatever its status', () => {
    expect(isSeasonReport(reportScoped('tournament', 'completed'))).toBe(false);
    expect(isSeasonReport(reportScoped('tournament', 'in_progress'))).toBe(false);
  });
  it('does not read a null tournament status as a season report', () => {
    expect(isSeasonReport(reportScoped('tournament', null))).toBe(false);
  });
});

function reportRow(round: number): ReportCell {
  return {
    round,
    golferName: `Golfer ${round}`,
    golferId: `g-${round}`,
    positionStr: 'T1',
    finish: { position: 1, tied: true },
    earnings: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
  };
}

describe('teamCellsByRound', () => {
  it('indexes a team\u2019s rows by round number', () => {
    const team: ReportTeamColumn = {
      teamId: 't-1',
      teamName: 'Aces',
      cells: [reportRow(2), reportRow(5)],
      topTenEarnings: 0,
      weeklyTotal: 0,
      previous: 0,
      subtotal: 0,
      topTenCount: 0,
      topTenMoney: 0,
      sideBets: 0,
      totalCash: 0,
    };
    const map = teamCellsByRound(team);
    expect(map.get(2)?.golferName).toBe('Golfer 2');
    expect(map.get(5)?.golferName).toBe('Golfer 5');
    expect(map.has(1)).toBe(false);
  });
});

describe('roundCellBg', () => {
  it('prefers the side-bet winner background over earnings', () => {
    expect(roundCellBg(true, true)).toBe('bg-side-bet-winner');
  });
  it('uses the earnings background when only earnings are present', () => {
    expect(roundCellBg(true, false)).toBe('bg-gold-bright');
  });
  it('returns no background when neither applies', () => {
    expect(roundCellBg(false, false)).toBe('');
  });
});

describe('roundTextColor', () => {
  it('uses dark text only when earnings are present without a side-bet win', () => {
    expect(roundTextColor(true, false)).toBe('text-on-gold');
    expect(roundTextColor(true, true)).toBe('text-ink-body');
    expect(roundTextColor(false, false)).toBe('text-ink-body');
  });
});
