import { describe, expect, it } from 'vitest';
import type { ReportCell, ReportTeamColumn, ReportTournamentInfo, WeeklyReport } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { makeLeaderboardEntry, makeWonLostTotals } from '@/shared/test/fixtures';
import {
  formatScoreToPar,
  hasPlayerOn,
  hasUndraftedPlayer,
  leaderboardTeamNames,
  playerNames,
  scoreboardSubtitle,
  scoreboardTeams,
  teamLabel,
} from './scoreboardModel';

function row(overrides: Partial<ReportCell> = {}): ReportCell {
  return {
    round: 1,
    golferName: 'Scottie Scheffler',
    golferId: 'g-1',
    positionStr: 'T1',
    finish: { position: 1, tied: true },
    earnings: 100,
    ownershipPct: 100,
    seasonEarnings: 100,
    seasonTopTens: 1,
    ...overrides,
  };
}

function teamColumn(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    cells: [row()],
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
  const teams = overrides.teams ?? [];
  return {
    scope: 'tournament',
    tournament: {
      name: 'Sample Open',
      startDate: '2026-03-01',
      endDate: '2026-03-04',
      status: 'completed',
      payoutMultiplier: 1,
      week: '9',
    },
    teams,
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
    weeklyStandings: teams.map((team) => team.teamId),
    ...overrides,
  };
}

describe('scoreboardTeams', () => {
  it('uses per-tournament top-10 earnings, not cumulative season earnings', () => {
    const teams = scoreboardTeams(
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
    expect(nth(teams, 0).topTenEarnings).toBe(42);
  });

  it('lays the teams out in the server\'s weekly standings, even against their weeklyTotal', () => {
    const teams = scoreboardTeams(
      report({
        teams: [
          teamColumn({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 10 }),
          teamColumn({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: 25 }),
          teamColumn({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: -5 }),
        ],
        weeklyStandings: ['t-3', 't-1', 't-2'],
      }),
    );
    expect(teams.map((t) => t.teamName)).toEqual(['Cuts', 'Aces', 'Birdies']);
  });

  it('includes only rows with positive earnings in golferScores', () => {
    const teams = scoreboardTeams(
      report({
        teams: [
          teamColumn({
            cells: [
              row({ golferName: 'Alpha', earnings: 50 }),
              row({ golferName: 'Beta', earnings: 0 }),
              row({ golferName: 'Gamma', earnings: 12 }),
            ],
          }),
        ],
      }),
    );
    const golfers = nth(teams, 0).golferScores.map((g) => g.golferName);
    expect(golfers).toEqual(['Alpha', 'Gamma']);
  });

  it('leaves a paid cell with no golfer out of golferScores', () => {
    const teams = scoreboardTeams(
      report({ teams: [teamColumn({ cells: [row({ golferName: null, earnings: 50 })] })] }),
    );
    expect(nth(teams, 0).golferScores).toEqual([]);
  });

  it('carries a tied finish through as its position and tie flag', () => {
    const teams = scoreboardTeams(
      report({
        teams: [teamColumn({ cells: [row({ positionStr: 'T5', finish: { position: 5, tied: true } })] })],
      }),
    );
    const golfer = nth(nth(teams, 0).golferScores, 0);
    expect(golfer.position).toBe(5);
    expect(golfer.tied).toBe(true);
  });

  it('carries a solo finish through as not tied', () => {
    const teams = scoreboardTeams(
      report({
        teams: [teamColumn({ cells: [row({ positionStr: '1', finish: { position: 1, tied: false } })] })],
      }),
    );
    const golfer = nth(nth(teams, 0).golferScores, 0);
    expect(golfer.position).toBe(1);
    expect(golfer.tied).toBe(false);
  });
});

describe('leaderboard labels', () => {
  const pair = makeLeaderboardEntry({
    players: [
      { name: 'McIlroy', teamNames: [] },
      { name: 'Lowry', teamNames: ['PAYRAY'] },
    ],
  });

  it('prints a lone player by name and a pair slash-joined in player order', () => {
    expect(playerNames(makeLeaderboardEntry({ players: [{ name: 'Solo', teamNames: [] }] }))).toBe('Solo');
    expect(playerNames(pair)).toBe('McIlroy / Lowry');
  });

  it('labels each partner in player order, an undrafted one as "undrafted"', () => {
    expect(teamLabel(pair)).toBe('undrafted / PAYRAY');
  });

  it('names every team rostering a golfer split across them', () => {
    const split = makeLeaderboardEntry({ players: [{ name: 'Rahm', teamNames: ['Aces', 'Birdies'] }] });
    expect(teamLabel(split)).toBe('Aces / Birdies');
  });
});

describe('leaderboard team filter', () => {
  const pair = makeLeaderboardEntry({
    players: [
      { name: 'McIlroy', teamNames: ['ROSEH2O'] },
      { name: 'Lowry', teamNames: ['PAYRAY', 'Aces'] },
    ],
  });
  const undrafted = makeLeaderboardEntry({ players: [{ name: 'Scheffler', teamNames: [] }] });

  it('lists each team on the leaderboard once, alphabetically', () => {
    const again = makeLeaderboardEntry({ players: [{ name: 'Rahm', teamNames: ['Aces'] }] });
    expect(leaderboardTeamNames([pair, undrafted, again])).toEqual(['Aces', 'PAYRAY', 'ROSEH2O']);
  });

  it('matches a row to a team that rosters either partner', () => {
    expect(hasPlayerOn(pair, 'PAYRAY')).toBe(true);
    expect(hasPlayerOn(pair, 'ROSEH2O')).toBe(true);
    expect(hasPlayerOn(pair, 'Birdies')).toBe(false);
  });

  it('matches a team by its whole name, however the name is spelled', () => {
    const awkward = makeLeaderboardEntry({ players: [{ name: 'Rahm', teamNames: ['undrafted / Aces'] }] });
    expect(hasPlayerOn(awkward, 'Aces')).toBe(false);
    expect(hasUndraftedPlayer(awkward)).toBe(false);
    expect(leaderboardTeamNames([awkward])).toEqual(['undrafted / Aces']);
  });

  it('counts a row undrafted when any partner has no team', () => {
    const half = makeLeaderboardEntry({
      players: [
        { name: 'McIlroy', teamNames: [] },
        { name: 'Lowry', teamNames: ['PAYRAY'] },
      ],
    });
    expect(hasUndraftedPlayer(half)).toBe(true);
    expect(hasUndraftedPlayer(undrafted)).toBe(true);
    expect(hasUndraftedPlayer(pair)).toBe(false);
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

describe('scoreboardSubtitle', () => {
  const tournament: ReportTournamentInfo = {
    name: 'Sample Open',
    startDate: '2026-03-01',
    endDate: '2026-03-04',
    status: 'completed',
    payoutMultiplier: 1,
    week: '9',
  };

  it('names the tournament and its week', () => {
    expect(scoreboardSubtitle(tournament)).toBe('Sample Open · Week 9');
  });
  it('adds the payout multiplier when it is not one', () => {
    expect(scoreboardSubtitle({ ...tournament, payoutMultiplier: 2 })).toBe('Sample Open · Week 9 · 2x payouts');
  });
  it('leaves out a missing name and marks a missing week', () => {
    expect(scoreboardSubtitle({ ...tournament, name: null, week: null })).toBe('Week ?');
  });
});
