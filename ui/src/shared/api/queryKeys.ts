import type { QueryClient, QueryKey } from '@tanstack/react-query';

/**
 * A root spelled at a call site can drift from the reader it must reach, and nothing fails but a
 * page that stays stale. Each narrower key spreads the wider one, so an invalidation by prefix
 * cannot drift from the keys beneath it.
 */
export const queryKeys = {
  /** @see queryKeys */
  auth: {
    root: ['auth'] as const,
    me: (): QueryKey => [...queryKeys.auth.root, 'me'],
  },

  /** @see queryKeys */
  leagues: {
    root: ['leagues'] as const,
  },

  /** @see queryKeys */
  seasons: {
    root: ['seasons'] as const,
    byLeague: (leagueId: string | null): QueryKey => [...queryKeys.seasons.root, leagueId],
  },

  /**
   * `current` sits under `bySeason` because the answer moves when a tournament is finalized or
   * reset, and every such write invalidates the season's listing.
   */
  tournaments: {
    root: ['tournaments'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.tournaments.root, seasonId],
    current: (seasonId: string | null): QueryKey => [...queryKeys.tournaments.bySeason(seasonId), 'current'],
  },

  /**
   * Its own root, not a second leaf beside `current`: an entity's keys form one chain. Staled by
   * `invalidateAfterScoreChange`, since only a write that finalizes or wipes scores moves it.
   */
  resettableTournaments: {
    root: ['resettableTournaments'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.resettableTournaments.root, seasonId],
  },

  /** @see queryKeys */
  tournamentCompetitors: {
    root: ['tournamentCompetitors'] as const,
    byTournament: (tournamentId: string): QueryKey => [...queryKeys.tournamentCompetitors.root, tournamentId],
  },

  /** @see queryKeys */
  rosters: {
    root: ['rosters'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.rosters.root, seasonId],
  },

  /** @see queryKeys */
  seasonRules: {
    root: ['seasonRules'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.seasonRules.root, seasonId],
  },

  /** @see queryKeys */
  defaultSeasonRules: {
    root: ['defaultSeasonRules'] as const,
  },

  /** @see queryKeys */
  dataHealth: {
    root: ['dataHealth'] as const,
  },

  /** @see queryKeys */
  golfers: {
    root: ['golfers'] as const,
    list: (): QueryKey => [...queryKeys.golfers.root, 'all'],
  },

  /** @see queryKeys */
  golferHistory: {
    root: ['golferHistory'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.golferHistory.root, seasonId],
    detail: (seasonId: string | null, golferId: string | null): QueryKey => [
      ...queryKeys.golferHistory.bySeason(seasonId),
      golferId,
    ],
  },

  /** @see queryKeys.report for the single-tournament report both hold. */
  tournamentReport: {
    root: ['tournamentReport'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.tournamentReport.root, seasonId],
    detail: (seasonId: string | null, tournamentId: string | null, live: boolean): QueryKey => [
      ...queryKeys.tournamentReport.bySeason(seasonId),
      tournamentId,
      live,
    ],
  },

  /**
   * The weekly pages cache a tournament's report here and the scoreboard caches it again under
   * `tournamentReport`, so a write that stales one invalidates both. An empty tournament id holds
   * the whole season's report.
   */
  report: {
    root: ['report'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.report.root, seasonId],
    detail: (seasonId: string | null, tournamentId: string | null, live: boolean): QueryKey => [
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
    detail: (seasonId: string | null, live: boolean, throughTournamentId: string | null): QueryKey => [
      ...queryKeys.rankings.bySeason(seasonId),
      live,
      throughTournamentId,
    ],
  },

  /** @see queryKeys.rankings for why the last two segments are not in its order. */
  playerRankings: {
    root: ['player-rankings'] as const,
    bySeason: (seasonId: string | null): QueryKey => [...queryKeys.playerRankings.root, seasonId],
    detail: (seasonId: string | null, throughTournamentId: string | null, live: boolean): QueryKey => [
      ...queryKeys.playerRankings.bySeason(seasonId),
      throughTournamentId,
      live,
    ],
  },
};

/**
 * `everySeason` is a variant and not an omitted id, so a handler reaches every season only by
 * naming it, never by leaving an argument out.
 */
export type ReportScope = { type: 'season'; seasonId: string | null } | { type: 'everySeason' };

interface SeasonScopedKeys {
  readonly root: QueryKey;
  readonly bySeason: (seasonId: string | null) => QueryKey;
}

// Each is built from scores through teams, rosters and player links, so a write to any of those
// stales all four.
const TEAM_REPORTS: readonly SeasonScopedKeys[] = [
  queryKeys.tournamentReport,
  queryKeys.report,
  queryKeys.rankings,
  queryKeys.playerRankings,
];

function scopedKey(keys: SeasonScopedKeys, scope: ReportScope): QueryKey {
  switch (scope.type) {
    case 'season':
      return keys.bySeason(scope.seasonId);
    case 'everySeason':
      return keys.root;
    default:
      return scope satisfies never;
  }
}

function invalidateEach(queryClient: QueryClient, stale: readonly SeasonScopedKeys[], scope: ReportScope): void {
  stale.forEach((keys) => {
    void queryClient.invalidateQueries({ queryKey: scopedKey(keys, scope) });
  });
}

/**
 * For a write that stores, deletes or finalizes scores. `golferHistory` and `resettableTournaments`
 * go stale after these alone: they read results and finalized tournaments, and no team, roster or
 * link row.
 */
export function invalidateAfterScoreChange(queryClient: QueryClient, scope: ReportScope): void {
  invalidateEach(queryClient, [...TEAM_REPORTS, queryKeys.golferHistory, queryKeys.resettableTournaments], scope);
}

/**
 * For a write that stores no score and moves which team one counts for: a roster, a player link.
 * @see invalidateAfterScoreChange for the reads this leaves fresh.
 */
export function invalidateAfterScoreOwnerChange(queryClient: QueryClient, scope: ReportScope): void {
  invalidateEach(queryClient, TEAM_REPORTS, scope);
}
