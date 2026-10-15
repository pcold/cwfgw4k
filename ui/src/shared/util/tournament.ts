import type { CurrentTournament, Tournament } from '@/shared/api/types';

/** The report pages' sentinel for the season-wide rollup, which the server keeps to finalized results. */
export const ALL_TOURNAMENTS = '';

export function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}

/**
 * Null until the server answers, so a page fetches nothing for a default it would then replace. A
 * finalized season opens on the rollup, which then holds every result.
 */
export function defaultReportSelection(current: CurrentTournament | undefined): string | null {
  if (current === undefined) return null;
  switch (current.type) {
    case 'unfinalized':
      return current.tournamentId;
    case 'season_finalized':
    case 'no_tournaments':
      return ALL_TOURNAMENTS;
    default:
      return current satisfies never;
  }
}

/** The scoreboard shows one tournament and has no rollup, so a finalized season opens on its last. */
export function defaultScoreboardSelection(current: CurrentTournament | undefined): string | null {
  if (current === undefined) return null;
  switch (current.type) {
    case 'unfinalized':
    case 'season_finalized':
      return current.tournamentId;
    case 'no_tournaments':
      return null;
    default:
      return current satisfies never;
  }
}
