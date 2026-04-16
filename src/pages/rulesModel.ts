import type { SeasonRules } from '@/api/types';

export type { SeasonRules };

// Fallback used while the season rules query is loading or if it errors.
// Kept in sync with the backend's SeasonRules.default.
export const DEFAULT_RULES: SeasonRules = {
  payouts: [18, 12, 10, 8, 7, 6, 5, 4, 3, 2],
  tieFloor: 1,
  sideBetRounds: [5, 6, 7, 8],
  sideBetAmount: 15,
};

export function ordinal(n: number): string {
  const suffixes = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return `${n}${suffixes[(v - 20) % 10] ?? suffixes[v] ?? suffixes[0]}`;
}
