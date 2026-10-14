import type { SeasonRules } from '@/shared/api/types';

export function formatSideBetRounds(rounds: readonly number[]): string {
  return rounds.join(',');
}

export function parseSideBetRounds(input: string): number[] {
  return input
    .split(',')
    .map((s) => Number.parseInt(s.trim(), 10))
    .filter((n) => Number.isFinite(n));
}

export function buildRules(input: {
  payouts: readonly number[];
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
