import { describe, expect, it } from 'vitest';
import type { ReportSideBetTeamEntry, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import { lateRowBetRounds, lateRowBetTeamTotals } from './lateRowBetsModel';

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
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
    liveEligible: false,
    awaitingTeeOff: false,
    finalizable: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
    sideBetStandings: [],
  };
}

describe('lateRowBetRounds', () => {
  it('returns an empty array when there is no side bet detail', () => {
    expect(lateRowBetRounds(baseReport())).toEqual([]);
  });

  it('carries every field of a ranked round onto its entries', () => {
    const report: WeeklyReport = {
      ...baseReport(),
      sideBetRules: { amountPerTeam: 20, rounds: [2] },
      sideBetDetail: [
        {
          round: 2,
          teams: [
            entry({ teamId: 't-1', teamName: 'Aces', cumulativeEarnings: 200, payout: 40, outcome: 'won' }),
            entry({ teamId: 't-3', teamName: 'Eagles', cumulativeEarnings: 100, payout: -20 }),
            entry({ teamId: 't-2', teamName: 'Birdies', cumulativeEarnings: 50, payout: -20 }),
          ],
        },
      ],
    };

    const rounds = lateRowBetRounds(report);
    expect(rounds).toHaveLength(1);
    expect(nth(rounds, 0).round).toBe(2);
    expect(nth(rounds, 0).entries.map((e) => e.teamName)).toEqual(['Aces', 'Eagles', 'Birdies']);
    expect(nth(rounds, 0).entries[0]).toMatchObject({
      teamId: 't-1',
      golferName: 'Golfer',
      cumulativeEarnings: 200,
      payout: 40,
      outcome: 'won',
    });
  });

  it("keeps a round's entries in the order they arrive, even against their cumulative earnings", () => {
    const report: WeeklyReport = {
      ...baseReport(),
      sideBetDetail: [
        {
          round: 2,
          teams: [
            entry({ teamId: 't-2', teamName: 'Birdies', cumulativeEarnings: 50 }),
            entry({ teamId: 't-1', teamName: 'Aces', cumulativeEarnings: 200 }),
            entry({ teamId: 't-3', teamName: 'Eagles', cumulativeEarnings: 100 }),
          ],
        },
      ],
    };
    const { entries } = nth(lateRowBetRounds(report), 0);
    expect(entries.map((e) => e.teamName)).toEqual(['Birdies', 'Aces', 'Eagles']);
  });

  it("keeps the server's outcome on every entry, a round nobody has earned in left null", () => {
    const report: WeeklyReport = {
      ...baseReport(),
      sideBetRules: { amountPerTeam: 20, rounds: [5, 6] },
      sideBetDetail: [
        {
          round: 5,
          teams: [
            entry({ teamId: 't-1', cumulativeEarnings: 0, payout: 0, outcome: 'won' }),
            entry({ teamId: 't-2', cumulativeEarnings: 0, payout: 0, outcome: 'won' }),
          ],
        },
        { round: 6, teams: [entry({ teamId: 't-1' }), entry({ teamId: 't-2' })] },
      ],
    };

    const outcomes = lateRowBetRounds(report).map((rd) => rd.entries.map((e) => e.outcome));
    expect(outcomes).toEqual([
      ['won', 'won'],
      [null, null],
    ]);
  });

  it("takes each entry's team name from the entry, whatever the teams list calls that team", () => {
    const report: WeeklyReport = {
      ...baseReport(),
      teams: [team({ teamId: 't-1', teamName: 'Renamed Since' })],
      sideBetRules: { amountPerTeam: 20, rounds: [3] },
      sideBetDetail: [{ round: 3, teams: [entry({ teamId: 't-1', teamName: 'Aces' })] }],
    };
    const { entries } = nth(lateRowBetRounds(report), 0);
    expect(nth(entries, 0).teamName).toBe('Aces');
  });
});

describe('lateRowBetTeamTotals', () => {
  const teams = [
    team({ teamId: 't-1', teamName: 'Aces', sideBets: -30 }),
    team({ teamId: 't-2', teamName: 'Birdies', sideBets: 60 }),
    team({ teamId: 't-3', teamName: 'Eagles', sideBets: 0 }),
  ];

  it("lays the totals out in the server's side-bet standings", () => {
    const report: WeeklyReport = { ...baseReport(), teams, sideBetStandings: ['t-2', 't-3', 't-1'] };
    expect(lateRowBetTeamTotals(report)).toEqual([
      { teamId: 't-2', teamName: 'Birdies', sideBets: 60 },
      { teamId: 't-3', teamName: 'Eagles', sideBets: 0 },
      { teamId: 't-1', teamName: 'Aces', sideBets: -30 },
    ]);
  });

  it('keeps the standings order it is sent, even against their sideBets', () => {
    const report: WeeklyReport = { ...baseReport(), teams, sideBetStandings: ['t-1', 't-3', 't-2'] };
    expect(lateRowBetTeamTotals(report).map((t) => t.teamName)).toEqual(['Aces', 'Eagles', 'Birdies']);
  });

  it('skips a standings id the teams list does not carry', () => {
    const report: WeeklyReport = { ...baseReport(), teams, sideBetStandings: ['t-2', 't-9', 't-3', 't-1'] };
    expect(lateRowBetTeamTotals(report).map((t) => t.teamId)).toEqual(['t-2', 't-3', 't-1']);
  });
});
