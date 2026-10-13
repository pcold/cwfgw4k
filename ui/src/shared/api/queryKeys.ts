import type { QueryKey } from '@tanstack/react-query';

/**
 * A root spelled at a call site can drift from the reader it must reach, and nothing fails but a
 * page that stays stale. Each narrower key spreads the wider one, so an invalidation by prefix
 * cannot drift from the keys beneath it.
 */
export const queryKeys = {
  auth: {
    root: ['auth'] as const,
    me: (): QueryKey => [...queryKeys.auth.root, 'me'],
  },

  leagues: {
    root: ['leagues'] as const,
  },

  seasons: {
    root: ['seasons'] as const,
    byLeague: (leagueId: string | null): QueryKey => [...queryKeys.seasons.root, leagueId],
  },

  tournaments: {
    root: ['tournaments'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.tournaments.root, seasonId],
  },

  tournamentCompetitors: {
    root: ['tournamentCompetitors'] as const,
    byTournament: (tournamentId: string): QueryKey => [
      ...queryKeys.tournamentCompetitors.root,
      tournamentId,
    ],
  },

  rosters: {
    root: ['rosters'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.rosters.root, seasonId],
  },

  seasonRules: {
    root: ['seasonRules'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.seasonRules.root, seasonId],
  },

  golfers: {
    root: ['golfers'] as const,
    list: (): QueryKey => [...queryKeys.golfers.root, 'all'],
  },

  golferHistory: {
    root: ['golferHistory'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.golferHistory.root, seasonId],
    detail: (seasonId: string | null, golferId: string | null): QueryKey => [
      ...queryKeys.golferHistory.bySeason(seasonId),
      golferId,
    ],
  },

  tournamentReport: {
    root: ['tournamentReport'] as const,
    bySeason: (seasonId: string | null): QueryKey => [
      ...queryKeys.tournamentReport.root,
      seasonId,
    ],
    detail: (seasonId: string | null, tournamentId: string | null, live: boolean): QueryKey => [
      ...queryKeys.tournamentReport.bySeason(seasonId),
      tournamentId,
      live,
    ],
  },

  report: {
    root: ['report'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.report.root, seasonId],
    detail: (seasonId: string | null, tournamentId: string, live: boolean): QueryKey => [
      ...queryKeys.report.bySeason(seasonId),
      tournamentId,
      live,
    ],
  },

  /**
   * `live` precedes the through-tournament here and follows it in `playerRankings`. Left apart
   * deliberately (CWF-101): writers invalidate by `bySeason`, which is the same under either
   * order, so converging buys nothing until an invalidation selects on `live`.
   */
  rankings: {
    root: ['rankings'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.rankings.root, seasonId],
    detail: (
      seasonId: string | null,
      live: boolean,
      throughTournamentId: string | null,
    ): QueryKey => [...queryKeys.rankings.bySeason(seasonId), live, throughTournamentId],
  },

  /** @see queryKeys.rankings for why the last two segments are not in its order. */
  playerRankings: {
    root: ['player-rankings'] as const,
    bySeason: (seasonId: string | null): QueryKey => [
      ...queryKeys.playerRankings.root,
      seasonId,
    ],
    detail: (
      seasonId: string | null,
      throughTournamentId: string | null,
      live: boolean,
    ): QueryKey => [...queryKeys.playerRankings.bySeason(seasonId), throughTournamentId, live],
  },
};
