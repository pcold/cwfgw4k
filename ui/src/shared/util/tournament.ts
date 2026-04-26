import type { Tournament } from '@/shared/api/types';

export function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}

/**
 * Pick the earliest non-completed tournament. The API returns tournaments
 * sorted by start date ascending, so the first non-completed entry is the
 * one chronologically next up. If everything is completed, fall back to the
 * earliest tournament so the UI still has a default selection.
 */
export function earliestUnfinalized(tournaments: Tournament[]): string | null {
  if (tournaments.length === 0) return null;
  const firstUnfinalized = tournaments.find((t) => t.status !== 'completed');
  return (firstUnfinalized ?? tournaments[0]).id;
}
