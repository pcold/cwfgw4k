import type { Tournament } from '@/api/types';

export function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}
