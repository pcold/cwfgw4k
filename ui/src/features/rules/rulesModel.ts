import type { SeasonRules } from '@/shared/api/types';

export type { SeasonRules };

/** @see payoutsByPlace */
export interface PlacePayout {
  readonly place: number;
  readonly amount: number;
}

/** `payouts` is in finishing order, so an amount's place is its position in it, from 1. */
export function payoutsByPlace(payouts: readonly number[]): readonly PlacePayout[] {
  return payouts.map((amount, index) => ({ place: index + 1, amount }));
}

export function ordinal(n: number): string {
  const suffixes = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return `${n}${suffixes[(v - 20) % 10] ?? suffixes[v] ?? suffixes[0]}`;
}
