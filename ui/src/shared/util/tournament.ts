import type { Tournament } from '@/shared/api/types';

export function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}

/**
 * Pick the earliest non-completed tournament — what the report pages want
 * as their default selection while the season is in progress. Null when
 * everything is completed (or the list is empty); callers decide the
 * fallback (an "All Tournaments" rollup for report pages,
 * `defaultScoreboardTournament` for the scoreboard).
 */
export function earliestUnfinalized(tournaments: Tournament[]): string | null {
  return tournaments.find((t) => t.status !== 'completed')?.id ?? null;
}

/**
 * Default tournament for the scoreboard view. Once the season is fully
 * finalized the scoreboard still wants a single tournament selected (no
 * "All" rollup), and the most useful one is the most recent.
 */
export function defaultScoreboardTournament(tournaments: Tournament[]): string | null {
  if (tournaments.length === 0) return null;
  return earliestUnfinalized(tournaments) ?? tournaments[tournaments.length - 1].id;
}
