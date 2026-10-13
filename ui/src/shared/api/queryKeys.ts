import type { QueryKey } from '@tanstack/react-query';

/**
 * A root spelled at a call site parts a reader from the invalidation meant to reach it, and nothing
 * fails but a page that stays stale. Each narrower key spreads the wider one, so an invalidation by
 * prefix cannot drift from the keys beneath it.
 */

export const authKeys = {
  root: ['auth'] as const,
  me: (): QueryKey => [...authKeys.root, 'me'],
};

export const leagueKeys = {
  root: ['leagues'] as const,
};

export const seasonKeys = {
  root: ['seasons'] as const,
  byLeague: (leagueId: string | null): QueryKey => [...seasonKeys.root, leagueId],
};

export const tournamentKeys = {
  root: ['tournaments'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...tournamentKeys.root, seasonId],
};

export const tournamentCompetitorKeys = {
  root: ['tournamentCompetitors'] as const,
  byTournament: (tournamentId: string): QueryKey => [
    ...tournamentCompetitorKeys.root,
    tournamentId,
  ],
};

export const rosterKeys = {
  root: ['rosters'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...rosterKeys.root, seasonId],
};

export const seasonRulesKeys = {
  root: ['seasonRules'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...seasonRulesKeys.root, seasonId],
};

export const golferKeys = {
  root: ['golfers'] as const,
  list: (): QueryKey => [...golferKeys.root, 'all'],
};

export const golferHistoryKeys = {
  root: ['golferHistory'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...golferHistoryKeys.root, seasonId],
  detail: (seasonId: string | null, golferId: string | null): QueryKey => [
    ...golferHistoryKeys.bySeason(seasonId),
    golferId,
  ],
};

export const tournamentReportKeys = {
  root: ['tournamentReport'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...tournamentReportKeys.root, seasonId],
  detail: (seasonId: string | null, tournamentId: string | null, live: boolean): QueryKey => [
    ...tournamentReportKeys.bySeason(seasonId),
    tournamentId,
    live,
  ],
};

export const reportKeys = {
  root: ['report'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...reportKeys.root, seasonId],
  detail: (seasonId: string | null, tournamentId: string, live: boolean): QueryKey => [
    ...reportKeys.bySeason(seasonId),
    tournamentId,
    live,
  ],
};

/**
 * `live` precedes the through-tournament here and follows it in {@link playerRankingsKeys}. Left
 * apart deliberately (CWF-101): writers invalidate by `bySeason`, which is the same under either
 * order, so converging buys nothing until an invalidation selects on `live`.
 */
export const rankingsKeys = {
  root: ['rankings'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...rankingsKeys.root, seasonId],
  detail: (
    seasonId: string | null,
    live: boolean,
    throughTournamentId: string | null,
  ): QueryKey => [...rankingsKeys.bySeason(seasonId), live, throughTournamentId],
};

/** @see rankingsKeys for why the last two segments are not in its order. */
export const playerRankingsKeys = {
  root: ['player-rankings'] as const,
  bySeason: (seasonId: string | null): QueryKey => [...playerRankingsKeys.root, seasonId],
  detail: (
    seasonId: string | null,
    throughTournamentId: string | null,
    live: boolean,
  ): QueryKey => [...playerRankingsKeys.bySeason(seasonId), throughTournamentId, live],
};
