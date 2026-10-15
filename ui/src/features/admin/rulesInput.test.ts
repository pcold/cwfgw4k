import { describe, expect, it } from 'vitest';
import { buildRules, formatSideBetRounds, isReadableSideBetRounds, parseSideBetRounds } from './rulesInput';

describe('formatSideBetRounds', () => {
  it('writes the rounds in the comma-separated form parseSideBetRounds reads back', () => {
    expect(formatSideBetRounds([3, 4, 9])).toBe('3,4,9');
    expect(parseSideBetRounds(formatSideBetRounds([3, 4, 9]))).toEqual([3, 4, 9]);
  });

  it('writes no rounds as an empty field', () => {
    expect(formatSideBetRounds([])).toBe('');
  });
});

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

describe('isReadableSideBetRounds', () => {
  it.each(['5,6,7,8', ' 1 , 2 ', '', '5,,6'])('reads %j whole', (input) => {
    expect(isReadableSideBetRounds(input)).toBe(true);
  });

  it.each(['five', '5-8', '5 6 7 8', '5,six', '1.5', '-1'])('refuses %j, which would parse to fewer rounds', (input) => {
    expect(isReadableSideBetRounds(input)).toBe(false);
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
