import type { CurrentTournament, Tournament, TournamentStatus } from '@/shared/api/types';

/** The report pages' sentinel for the season-wide rollup, which the server keeps to finalized results. */
export const ALL_TOURNAMENTS = '';

/** The words `StatusChip` shows, so a week's option and its page title agree. */
const STATUS_LABEL: Readonly<Record<TournamentStatus, string>> = {
  upcoming: 'Upcoming',
  in_progress: 'Live',
  completed: 'Final',
};

export function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `${tournamentWeekLabel(t)} — ${t.name}${multiplier} — ${STATUS_LABEL[t.status]}`;
}

/** For a line too narrow for the status and multiplier, which the select's options still carry. */
export function tournamentShortLabel(t: Tournament): string {
  return `${tournamentWeekLabel(t)} · ${t.name}`;
}

/** @see tournamentShortLabel */
export function tournamentWeekLabel(t: Tournament): string {
  return `Wk ${t.week ?? '?'}`;
}

/**
 * Null once the loaded list lacks the pick, so a season change needs no handler and an answer the
 * cached list trails selects no option the dropdown lacks.
 */
export function pickInSeason(pick: string | null, tournaments: readonly Tournament[]): string | null {
  if (pick === ALL_TOURNAMENTS) return pick;
  return tournaments.some((t) => t.id === pick) ? pick : null;
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
