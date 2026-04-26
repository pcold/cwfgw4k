import { describe, expect, it } from 'vitest';
import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import { collapsePairs, deriveScoreboard, formatScoreToPar } from './scoreboardModel';
import type { LeaderboardEntry } from './scoreboardModel';

function row(overrides: Partial<ReportRow> = {}): ReportRow {
  return {
    round: 1,
    golferName: 'Scottie Scheffler',
    golferId: 'g-1',
    positionStr: 'T1',
    scoreToPar: '-12',
    earnings: 100,
    topTens: 1,
    ownershipPct: 100,
    seasonEarnings: 100,
    seasonTopTens: 1,
    pairKey: null,
    ...overrides,
  };
}

function teamColumn(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    rows: [row()],
    topTenEarnings: 1,
    weeklyTotal: 18,
    previous: 0,
    subtotal: 18,
    topTenCount: 1,
    topTenMoney: 18,
    sideBets: 0,
    totalCash: 18,
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

describe('deriveScoreboard', () => {
  it('uses per-tournament top-10 earnings, not cumulative season earnings', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({
            teamId: 't-1',
            teamName: 'Aces',
            topTenEarnings: 42,
            topTenMoney: 999,
          }),
        ],
      }),
    );
    expect(scoreboard.teams[0].topTenEarnings).toBe(42);
  });

  it('sorts teams by weeklyTotal descending', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 10 }),
          teamColumn({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: 25 }),
          teamColumn({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: -5 }),
        ],
      }),
    );
    expect(scoreboard.teams.map((t) => t.teamName)).toEqual(['Birdies', 'Aces', 'Cuts']);
  });

  it('includes only rows with positive earnings in golferScores', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({
            rows: [
              row({ golferName: 'Alpha', earnings: 50 }),
              row({ golferName: 'Beta', earnings: 0 }),
              row({ golferName: 'Gamma', earnings: 12 }),
            ],
          }),
        ],
      }),
    );
    const golfers = scoreboard.teams[0].golferScores.map((g) => g.golferName);
    expect(golfers).toEqual(['Alpha', 'Gamma']);
  });

  it('parses position strings like "T5" to numbers and marks them tied', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [teamColumn({ rows: [row({ positionStr: 'T5' })] })],
      }),
    );
    expect(scoreboard.teams[0].golferScores[0].position).toBe(5);
    expect(scoreboard.teams[0].golferScores[0].tied).toBe(true);
  });

  it('marks a solo position (no T prefix) as not tied', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [teamColumn({ rows: [row({ positionStr: '1' })] })],
      }),
    );
    expect(scoreboard.teams[0].golferScores[0].position).toBe(1);
    expect(scoreboard.teams[0].golferScores[0].tied).toBe(false);
  });

  it('computes the zero-sum won/lost/net summary', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({ teamId: 't-1', weeklyTotal: 30 }),
          teamColumn({ teamId: 't-2', weeklyTotal: 10 }),
          teamColumn({ teamId: 't-3', weeklyTotal: -40 }),
        ],
      }),
    );
    expect(scoreboard.totalWon).toBe(40);
    expect(scoreboard.totalLost).toBe(-40);
    expect(scoreboard.net).toBe(0);
  });

  it('builds a leaderboard with rostered + undrafted, dedupes by name, caps at 20', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({
            teamName: 'Aces',
            rows: [
              row({ golferName: 'Scheffler', positionStr: 'T1', scoreToPar: '-12' }),
              row({ golferName: 'Rahm', positionStr: 'T5', scoreToPar: '-6' }),
            ],
          }),
          teamColumn({
            teamId: 't-2',
            teamName: 'Birdies',
            rows: [
              // same Rahm, rostered by both teams — dedup should keep one
              row({ golferName: 'Rahm', positionStr: 'T5', scoreToPar: '-6' }),
            ],
          }),
        ],
        undraftedTopTens: [
          { name: 'Unknown1', position: 3, payout: 0, scoreToPar: '-10', pairKey: null },
        ],
      }),
    );
    const names = scoreboard.leaderboard.map((e) => e.name);
    expect(names).toContain('Scheffler');
    expect(names).toContain('Rahm');
    expect(names).toContain('Unknown1');
    // dedupe: Rahm appears once
    expect(names.filter((n) => n === 'Rahm')).toHaveLength(1);
    // sorted by position ascending
    expect(scoreboard.leaderboard[0].name).toBe('Scheffler');
  });

  it('uses liveLeaderboard when present and ignores rostered row fallback', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({
            teamName: 'Aces',
            // Rostered row uses last-name spelling and would normally feed the leaderboard
            rows: [row({ golferName: 'SCHEFFLER', positionStr: 'T1', scoreToPar: '-12' })],
          }),
        ],
        undraftedTopTens: [
          { name: 'Old Undrafted', position: 9, payout: 0, scoreToPar: '-2', pairKey: null },
        ],
        liveLeaderboard: [
          { name: 'Scottie Scheffler', position: 1, scoreToPar: '-12', rostered: true, teamName: 'Aces', pairKey: null },
          { name: 'Rory McIlroy', position: 2, scoreToPar: '-10', rostered: false, teamName: null, pairKey: null },
          { name: 'Phil Mickelson', position: 5, scoreToPar: '-7', rostered: false, teamName: null, pairKey: null },
        ],
      }),
    );
    expect(scoreboard.leaderboard.map((e) => e.name)).toEqual([
      'Scottie Scheffler',
      'Rory McIlroy',
      'Phil Mickelson',
    ]);
    expect(scoreboard.leaderboard[1].rostered).toBe(false);
    expect(scoreboard.leaderboard[1].scoreToPar).toBe(-10);
    // The legacy fallback names ('SCHEFFLER', 'Old Undrafted') must not appear
    expect(scoreboard.leaderboard.find((e) => e.name === 'SCHEFFLER')).toBeUndefined();
    expect(scoreboard.leaderboard.find((e) => e.name === 'Old Undrafted')).toBeUndefined();
  });

  it('marks rostered vs undrafted leaderboard entries', () => {
    const scoreboard = deriveScoreboard(
      report({
        teams: [
          teamColumn({
            teamName: 'Aces',
            rows: [row({ golferName: 'Tiger', positionStr: 'T10' })],
          }),
        ],
        undraftedTopTens: [
          { name: 'Phil', position: 4, payout: 0, scoreToPar: 'E', pairKey: null },
        ],
      }),
    );
    const phil = scoreboard.leaderboard.find((e) => e.name === 'Phil');
    const tiger = scoreboard.leaderboard.find((e) => e.name === 'Tiger');
    expect(phil?.rostered).toBe(false);
    expect(phil?.teamName).toBe(null);
    expect(phil?.scoreToPar).toBe(0);
    expect(tiger?.rostered).toBe(true);
    expect(tiger?.teamName).toBe('Aces');
  });

  it('collapses team-event pairs from liveLeaderboard into a single row', () => {
    const scoreboard = deriveScoreboard(
      report({
        liveLeaderboard: [
          { name: 'McIlroy', position: 1, scoreToPar: '-28', rostered: false, teamName: null, pairKey: 'team:99' },
          { name: 'Lowry', position: 1, scoreToPar: '-28', rostered: true, teamName: 'PAYRAY', pairKey: 'team:99' },
          { name: 'Scheffler', position: 3, scoreToPar: '-25', rostered: false, teamName: null, pairKey: null },
        ],
      }),
    );
    expect(scoreboard.leaderboard).toHaveLength(2);
    expect(scoreboard.leaderboard[0].name).toBe('McIlroy / Lowry');
    expect(scoreboard.leaderboard[0].teamName).toBe('undrafted / PAYRAY');
    expect(scoreboard.leaderboard[0].rostered).toBe(true);
    expect(scoreboard.leaderboard[1].name).toBe('Scheffler');
  });
});

describe('collapsePairs', () => {
  const entry = (over: Partial<LeaderboardEntry>): LeaderboardEntry => ({
    name: 'x',
    position: 1,
    scoreToPar: 0,
    rostered: false,
    teamName: null,
    pairKey: null,
    ...over,
  });

  it('keeps non-pair entries untouched', () => {
    const input = [entry({ name: 'Solo' })];
    expect(collapsePairs(input)).toEqual(input);
  });

  it('joins two entries sharing a pairKey with / separator in matching order', () => {
    const input = [
      entry({ name: 'A', pairKey: 'p1', teamName: 'X' }),
      entry({ name: 'B', pairKey: 'p1', teamName: 'Y' }),
    ];
    const out = collapsePairs(input);
    expect(out).toHaveLength(1);
    expect(out[0].name).toBe('A / B');
    expect(out[0].teamName).toBe('X / Y');
  });

  it('fills in "undrafted" when a partner has no team', () => {
    const input = [
      entry({ name: 'A', pairKey: 'p1', teamName: null }),
      entry({ name: 'B', pairKey: 'p1', teamName: 'WOMBLE' }),
    ];
    const out = collapsePairs(input);
    expect(out[0].teamName).toBe('undrafted / WOMBLE');
  });

  it('marks the collapsed row as rostered if either partner is rostered', () => {
    const input = [
      entry({ name: 'A', pairKey: 'p1', rostered: false }),
      entry({ name: 'B', pairKey: 'p1', rostered: true }),
    ];
    expect(collapsePairs(input)[0].rostered).toBe(true);
  });

  it('keeps distinct pairKeys as separate rows', () => {
    const input = [
      entry({ name: 'A', pairKey: 'p1' }),
      entry({ name: 'B', pairKey: 'p1' }),
      entry({ name: 'C', pairKey: 'p2' }),
      entry({ name: 'D', pairKey: 'p2' }),
    ];
    const out = collapsePairs(input);
    expect(out.map((r) => r.name)).toEqual(['A / B', 'C / D']);
  });
});

describe('formatScoreToPar', () => {
  it('formats null as em dash', () => {
    expect(formatScoreToPar(null)).toBe('—');
  });
  it('formats zero as E', () => {
    expect(formatScoreToPar(0)).toBe('E');
  });
  it('prefixes positive values with +', () => {
    expect(formatScoreToPar(5)).toBe('+5');
  });
  it('passes negative values through', () => {
    expect(formatScoreToPar(-7)).toBe('-7');
  });
});
