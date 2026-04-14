import type { SeasonRules } from '../../api/types';

export const DEFAULT_PAYOUTS: number[] = [18, 12, 10, 8, 7, 6, 5, 4, 3, 2];
export const DEFAULT_TIE_FLOOR = 1;
export const DEFAULT_SIDE_BET_ROUNDS_STR = '5,6,7,8';
export const DEFAULT_SIDE_BET_AMOUNT = 15;

export function parseSideBetRounds(input: string): number[] {
  return input
    .split(',')
    .map((s) => Number.parseInt(s.trim(), 10))
    .filter((n) => Number.isFinite(n));
}

export function buildRules(input: {
  payouts: number[];
  tieFloor: number;
  sideBetRoundsStr: string;
  sideBetAmount: number;
}): SeasonRules {
  return {
    payouts: input.payouts.map((n) => Number(n)),
    tieFloor: Number(input.tieFloor),
    sideBetRounds: parseSideBetRounds(input.sideBetRoundsStr),
    sideBetAmount: Number(input.sideBetAmount),
  };
}
