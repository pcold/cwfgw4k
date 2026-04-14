export interface SeasonRules {
  payouts: number[];
  tieFloor: number;
  sideBetRounds: number[];
  sideBetAmount: number;
}

// Mirrors the hardcoded fallback in the legacy Alpine UI's currentRules().
// The backend does not currently expose a rules object on GET /seasons, so
// both UIs render these defaults regardless of what was posted on create.
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
