import { expect, it } from 'vitest';
import { QueryClient, type QueryKey } from '@tanstack/react-query';
import { nth } from '@/shared/test/nth';
import {
  invalidateAfterScoreChange,
  invalidateAfterScoreOwnerChange,
  queryKeys,
  type ReportScope,
} from './queryKeys';

// Widest key first, one entry per key the entity builds. The Record makes an entity added without a
// chain a compile error, since nothing below would check it.
const KEY_CHAINS: Record<keyof typeof queryKeys, readonly QueryKey[]> = {
  auth: [queryKeys.auth.root, queryKeys.auth.me()],
  leagues: [queryKeys.leagues.root],
  seasons: [queryKeys.seasons.root, queryKeys.seasons.byLeague('lg-1')],
  tournaments: [queryKeys.tournaments.root, queryKeys.tournaments.bySeason('sn-1')],
  tournamentCompetitors: [
    queryKeys.tournamentCompetitors.root,
    queryKeys.tournamentCompetitors.byTournament('tn-1'),
  ],
  rosters: [queryKeys.rosters.root, queryKeys.rosters.bySeason('sn-1')],
  seasonRules: [queryKeys.seasonRules.root, queryKeys.seasonRules.bySeason('sn-1')],
  golfers: [queryKeys.golfers.root, queryKeys.golfers.list()],
  golferHistory: [
    queryKeys.golferHistory.root,
    queryKeys.golferHistory.bySeason('sn-1'),
    queryKeys.golferHistory.detail('sn-1', 'g-1'),
  ],
  tournamentReport: [
    queryKeys.tournamentReport.root,
    queryKeys.tournamentReport.bySeason('sn-1'),
    queryKeys.tournamentReport.detail('sn-1', 'tn-1', true),
  ],
  report: [
    queryKeys.report.root,
    queryKeys.report.bySeason('sn-1'),
    queryKeys.report.detail('sn-1', 'tn-1', true),
  ],
  rankings: [
    queryKeys.rankings.root,
    queryKeys.rankings.bySeason('sn-1'),
    queryKeys.rankings.detail('sn-1', true, 'tn-1'),
  ],
  playerRankings: [
    queryKeys.playerRankings.root,
    queryKeys.playerRankings.bySeason('sn-1'),
    queryKeys.playerRankings.detail('sn-1', null, true),
  ],
};

// The one place a root is spelled outside its factory. A page test cannot catch an edited root —
// reader and invalidation move together — so this pin is what makes a rename a deliberate edit.
it('pins the value of every key, root first', () => {
  expect(KEY_CHAINS).toEqual({
    auth: [['auth'], ['auth', 'me']],
    leagues: [['leagues']],
    seasons: [['seasons'], ['seasons', 'lg-1']],
    tournaments: [['tournaments'], ['tournaments', 'sn-1']],
    tournamentCompetitors: [['tournamentCompetitors'], ['tournamentCompetitors', 'tn-1']],
    rosters: [['rosters'], ['rosters', 'sn-1']],
    seasonRules: [['seasonRules'], ['seasonRules', 'sn-1']],
    golfers: [['golfers'], ['golfers', 'all']],
    golferHistory: [['golferHistory'], ['golferHistory', 'sn-1'], ['golferHistory', 'sn-1', 'g-1']],
    tournamentReport: [
      ['tournamentReport'],
      ['tournamentReport', 'sn-1'],
      ['tournamentReport', 'sn-1', 'tn-1', true],
    ],
    report: [['report'], ['report', 'sn-1'], ['report', 'sn-1', 'tn-1', true]],
    rankings: [['rankings'], ['rankings', 'sn-1'], ['rankings', 'sn-1', true, 'tn-1']],
    playerRankings: [
      ['player-rankings'],
      ['player-rankings', 'sn-1'],
      ['player-rankings', 'sn-1', null, true],
    ],
  });
});

// Asked of the cache rather than of the arrays, so it holds to whatever matching invalidateQueries
// really does.
it.each(Object.entries(KEY_CHAINS))(
  'reaches every narrower %s key from each wider one',
  (_entity, chain) => {
    const queryClient = new QueryClient();
    chain.forEach((key) => queryClient.setQueryData(key, 'cached'));

    const reachedFrom = chain.map((key) =>
      queryClient
        .getQueryCache()
        .findAll({ queryKey: key })
        .map((query) => query.queryKey),
    );

    expect(reachedFrom).toEqual(chain.map((_key, index) => chain.slice(index)));
  },
);

// Without this a key added to an existing entity, built from a literal of its own, is checked by
// nothing above.
it('holds a chain entry for every key an entity builds', () => {
  const keysBuilt = Object.fromEntries(
    Object.entries(queryKeys).map(([entity, keys]) => [entity, Object.keys(keys).length]),
  );
  const keysChained = Object.fromEntries(
    Object.entries(KEY_CHAINS).map(([entity, chain]) => [entity, chain.length]),
  );

  expect(keysChained).toEqual(keysBuilt);
});

it('gives every entity a root of its own, so no invalidation can reach another entity', () => {
  const roots = Object.values(KEY_CHAINS).map((chain) => nth(chain, 0)[0]);

  expect(new Set(roots).size).toBe(roots.length);
});

// Each fan-out's membership, spelled apart from the list it pins: a key dropped there fails here by
// entity name, as well as in the refetch test of every handler that calls it.
const STALE_AFTER: [
  string,
  (queryClient: QueryClient, scope: ReportScope) => void,
  readonly (keyof typeof queryKeys)[],
][] = [
  [
    'a score change',
    invalidateAfterScoreChange,
    ['tournamentReport', 'report', 'rankings', 'playerRankings', 'golferHistory'],
  ],
  [
    'a score-owner change',
    invalidateAfterScoreOwnerChange,
    ['tournamentReport', 'report', 'rankings', 'playerRankings'],
  ],
];

function staleKeysByEntity(
  invalidate: (queryClient: QueryClient) => void,
): Record<string, readonly QueryKey[]> {
  const queryClient = new QueryClient();
  Object.values(KEY_CHAINS)
    .flat()
    .forEach((key) => queryClient.setQueryData(key, 'cached'));

  invalidate(queryClient);

  const staleKeys = Object.entries(KEY_CHAINS).map(([entity, chain]): [string, QueryKey[]] => [
    entity,
    chain.filter((key) => queryClient.getQueryState(key)?.isInvalidated === true),
  ]);
  return Object.fromEntries(staleKeys.filter(([, stale]) => stale.length > 0));
}

it.each(STALE_AFTER)(
  'invalidates every key of the reads %s stales, and of no other entity',
  (_write, invalidate, entities) => {
    const staleKeys = staleKeysByEntity((queryClient) =>
      invalidate(queryClient, { type: 'everySeason' }),
    );

    expect(staleKeys).toEqual(
      Object.fromEntries(entities.map((entity) => [entity, KEY_CHAINS[entity]])),
    );
  },
);

// Stale below its root and not at it, a chain was invalidated by season, which is what leaves
// every other season's queries alone.
it.each(STALE_AFTER)(
  'invalidates the same reads below their roots when %s names its season',
  (_write, invalidate, entities) => {
    const staleKeys = staleKeysByEntity((queryClient) =>
      invalidate(queryClient, { type: 'season', seasonId: 'sn-1' }),
    );

    expect(staleKeys).toEqual(
      Object.fromEntries(entities.map((entity) => [entity, KEY_CHAINS[entity].slice(1)])),
    );
  },
);
