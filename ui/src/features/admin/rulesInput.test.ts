import { describe, expect, it } from 'vitest';
import { buildRules, parseSideBetRounds } from './rulesInput';

describe('parseSideBetRounds', () => {
  it('parses a comma-separated list', () => {
    expect(parseSideBetRounds('5,6,7,8')).toEqual([5, 6, 7, 8]);
  });

  it('tolerates whitespace and drops non-numeric entries', () => {
    expect(parseSideBetRounds(' 1 , 2 ,nope, 3 ')).toEqual([1, 2, 3]);
  });

  it('returns an empty array for an empty string', () => {
    expect(parseSideBetRounds('')).toEqual([]);
  });
});

describe('buildRules', () => {
  it('coerces numeric inputs and parses side bet rounds', () => {
    expect(
      buildRules({
        payouts: [18, 12, 10],
        tieFloor: 1,
        sideBetRoundsStr: '5,6',
        sideBetAmount: 15,
      }),
    ).toEqual({
      payouts: [18, 12, 10],
      tieFloor: 1,
      sideBetRounds: [5, 6],
      sideBetAmount: 15,
    });
  });
});
