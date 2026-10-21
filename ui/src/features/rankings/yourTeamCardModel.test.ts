import { describe, expect, it } from 'vitest';
import type { Rankings, TeamRanking } from '@/shared/api/types';
import { findYourTeam, ordinal, placeLabel, standingStats } from './yourTeamCardModel';

function team(teamId: string, teamName: string, totalCash: number): TeamRanking {
  return { teamId, teamName, subtotal: totalCash, sideBets: 0, totalCash, series: [totalCash] };
}

const rankings: Rankings = {
  teams: [team('t-1', 'Poczik', 1082), team('t-2', 'Womble', -179)],
  weeks: ['11'],
  tournamentNames: ['Sample Open'],
  live: false,
  liveEligible: false,
  cashTotals: { won: 1082, lost: -179, net: 903 },
  standings: [
    { type: 'leader', teamId: 't-1', tied: false, aheadOf: { position: 2, tied: false, amount: 1261 } },
    {
      type: 'chaser',
      teamId: 't-2',
      tied: false,
      position: 2,
      behind: { position: 1, tied: false, amount: 1261 },
      behindLeader: 1261,
    },
  ],
};

describe('ordinal', () => {
  it.each([
    [1, '1st'],
    [2, '2nd'],
    [3, '3rd'],
    [4, '4th'],
    [10, '10th'],
    [11, '11th'],
    [12, '12th'],
    [13, '13th'],
    [21, '21st'],
    [22, '22nd'],
    [23, '23rd'],
    [101, '101st'],
    [111, '111th'],
    [112, '112th'],
  ])('writes %i as %s', (position, expected) => {
    expect(ordinal(position)).toBe(expected);
  });
});

describe('placeLabel', () => {
  it('prefixes a shared position with T', () => {
    expect(placeLabel(8, true)).toBe('T8th');
    expect(placeLabel(8, false)).toBe('8th');
  });
});

describe('standingStats', () => {
  it('gives a chaser its position, the gap to the next total up and the gap to the leader', () => {
    const stats = standingStats(
      {
        type: 'chaser',
        teamId: 't-8',
        tied: false,
        position: 8,
        behind: { position: 7, tied: false, amount: 23.25 },
        behindLeader: 1261.4,
      },
      13,
    );

    expect(stats).toEqual([
      { label: 'Pos', value: '8th / 13' },
      { label: 'Behind 7th', value: '+$23' },
      { label: 'Behind leader', value: '+$1,261' },
    ]);
  });

  it('marks a tie on either side of the gap', () => {
    const stats = standingStats(
      {
        type: 'chaser',
        teamId: 't-4',
        tied: true,
        position: 4,
        behind: { position: 2, tied: true, amount: 35 },
        behindLeader: 75,
      },
      13,
    );

    expect(stats.slice(0, 2)).toEqual([
      { label: 'Pos', value: 'T4th / 13' },
      { label: 'Behind T2nd', value: '+$35' },
    ]);
  });

  it('gives a leader its position and its lead over the next total down, and nothing behind', () => {
    const stats = standingStats(
      { type: 'leader', teamId: 't-1', tied: true, aheadOf: { position: 3, tied: false, amount: 40 } },
      13,
    );

    expect(stats).toEqual([
      { label: 'Pos', value: 'T1st / 13' },
      { label: 'Ahead of 3rd', value: '+$40' },
    ]);
  });

  it('gives a leader with nobody below it its position alone', () => {
    expect(standingStats({ type: 'leader', teamId: 't-1', tied: false, aheadOf: null }, 1)).toEqual([
      { label: 'Pos', value: '1st / 1' },
    ]);
  });
});

describe('findYourTeam', () => {
  it('pairs the chosen team with its standing, whatever the case of its name', () => {
    expect(findYourTeam(rankings, 'WOMBLE')).toEqual({ team: rankings.teams[1], standing: rankings.standings[1] });
  });

  it('finds nothing when no team is chosen', () => {
    expect(findYourTeam(rankings, null)).toBeNull();
  });

  it('finds nothing for a team these standings do not hold', () => {
    expect(findYourTeam(rankings, 'Kirk')).toBeNull();
  });

  it('finds nothing for a team the standings list leaves out', () => {
    expect(findYourTeam({ ...rankings, standings: [] }, 'Womble')).toBeNull();
  });
});
