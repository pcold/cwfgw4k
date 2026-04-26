import type { Tournament } from '@/shared/api/types';

export function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}

/** Pick the earliest unfinalized tournament (tournaments arrive newest-first from the API). */
export function earliestUnfinalized(tournaments: Tournament[]): string | null {
  if (tournaments.length === 0) return null;
  const unfinalized = tournaments.filter((t) => t.status !== 'completed');
  if (unfinalized.length > 0) return unfinalized[unfinalized.length - 1].id;
  return tournaments[0].id;
}
