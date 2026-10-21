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
