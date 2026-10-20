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
  cellEarningsLabel,
  isSeasonReport,
  reportSubtitle,
  sideBetAmountByRound,
  sideBetRowLabel,
  sideBetWinnersByRound,
  teamCellsByRound,
  topTensLabel,
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

describe('reportSubtitle', () => {
  const event: WeeklyReport = {
    ...reportScoped('tournament', 'completed'),
    tournament: {
      name: 'Players Championship',
      startDate: '2026-03-12',
      endDate: '2026-03-15',
      status: 'completed',
      payoutMultiplier: 2,
      week: '11',
    },
  };

  it('names the tournament, its dates and a payout multiplier other than one', () => {
    expect(reportSubtitle(event)).toBe('Players Championship · 2026-03-12 – 2026-03-15 · 2x payouts');
  });
  it('names a payout multiplier below one, since it changes the money as much as one above', () => {
    const halved = { ...event, tournament: { ...event.tournament, payoutMultiplier: 0.5 } };
    expect(reportSubtitle(halved)).toBe('Players Championship · 2026-03-12 – 2026-03-15 · 0.5x payouts');
  });
  it('says the season report holds the season totals', () => {
    const season = { ...reportScoped('season'), tournament: { ...event.tournament, name: 'All Tournaments', payoutMultiplier: 1 } };
    expect(reportSubtitle(season)).toBe('All Tournaments · Season totals');
  });
  it('leaves out the parts the report lacks', () => {
    expect(reportSubtitle(reportScoped('tournament'))).toBe('');
  });
});

describe('cellEarningsLabel', () => {
  it('pairs a paid finish with its earnings', () => {
    expect(cellEarningsLabel({ ...reportRow(1), positionStr: 'T3', earnings: 9.5 })).toBe('T3 - +$9.50');
  });

  it('prints a bare dash for a finish that paid nothing', () => {
    expect(cellEarningsLabel(reportRow(1))).toBe('-');
  });
});

describe('topTensLabel', () => {
  it.each([
    [0, '0 top 10s'],
    [1, '1 top 10'],
    [2, '2 top 10s'],
  ])('counts %i as %s', (count, label) => {
    expect(topTensLabel(count)).toBe(label);
  });
});

