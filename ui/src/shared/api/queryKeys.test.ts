import { expect, it } from 'vitest';
import { QueryClient, type QueryKey } from '@tanstack/react-query';
import {
  authKeys,
  golferHistoryKeys,
  golferKeys,
  leagueKeys,
  playerRankingsKeys,
  rankingsKeys,
  reportKeys,
  rosterKeys,
  seasonKeys,
  seasonRulesKeys,
  tournamentCompetitorKeys,
  tournamentKeys,
  tournamentReportKeys,
} from './queryKeys';

// One chain per factory, widest key first. A factory added without a chain here is checked by
// nothing below.
const KEY_CHAINS: readonly (readonly QueryKey[])[] = [
  [authKeys.root, authKeys.me()],
  [leagueKeys.root],
  [seasonKeys.root, seasonKeys.byLeague('lg-1')],
  [tournamentKeys.root, tournamentKeys.bySeason('sn-1')],
  [tournamentCompetitorKeys.root, tournamentCompetitorKeys.byTournament('tn-1')],
  [rosterKeys.root, rosterKeys.bySeason('sn-1')],
  [seasonRulesKeys.root, seasonRulesKeys.bySeason('sn-1')],
  [golferKeys.root, golferKeys.list()],
  [
    golferHistoryKeys.root,
    golferHistoryKeys.bySeason('sn-1'),
    golferHistoryKeys.detail('sn-1', 'g-1'),
  ],
  [
    tournamentReportKeys.root,
    tournamentReportKeys.bySeason('sn-1'),
    tournamentReportKeys.detail('sn-1', 'tn-1', true),
  ],
  [reportKeys.root, reportKeys.bySeason('sn-1'), reportKeys.detail('sn-1', 'tn-1', true)],
  [rankingsKeys.root, rankingsKeys.bySeason('sn-1'), rankingsKeys.detail('sn-1', true, 'tn-1')],
  [
    playerRankingsKeys.root,
    playerRankingsKeys.bySeason('sn-1'),
    playerRankingsKeys.detail('sn-1', 'tn-1', true),
  ],
];

// The one place a root is spelled outside its factory. A page test cannot catch an edited root —
// reader and invalidation move together — so this pin is what makes a rename a deliberate edit.
it('pins every key to the array its readers and invalidations have always used', () => {
  expect(KEY_CHAINS).toEqual([
    [['auth'], ['auth', 'me']],
    [['leagues']],
    [['seasons'], ['seasons', 'lg-1']],
    [['tournaments'], ['tournaments', 'sn-1']],
    [['tournamentCompetitors'], ['tournamentCompetitors', 'tn-1']],
    [['rosters'], ['rosters', 'sn-1']],
    [['seasonRules'], ['seasonRules', 'sn-1']],
    [['golfers'], ['golfers', 'all']],
    [['golferHistory'], ['golferHistory', 'sn-1'], ['golferHistory', 'sn-1', 'g-1']],
    [
      ['tournamentReport'],
      ['tournamentReport', 'sn-1'],
      ['tournamentReport', 'sn-1', 'tn-1', true],
    ],
    [['report'], ['report', 'sn-1'], ['report', 'sn-1', 'tn-1', true]],
    [['rankings'], ['rankings', 'sn-1'], ['rankings', 'sn-1', true, 'tn-1']],
    [
      ['player-rankings'],
      ['player-rankings', 'sn-1'],
      ['player-rankings', 'sn-1', 'tn-1', true],
    ],
  ]);
});

// Asked of the cache rather than of the arrays, so it holds to whatever matching invalidateQueries
// really does.
it.each(KEY_CHAINS.map((chain) => [chain[0][0], chain] as const))(
  'reaches every narrower %s key from each wider one',
  (_root, chain) => {
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

it('gives every entity a root of its own, so no invalidation can reach another entity', () => {
  const roots = KEY_CHAINS.map((chain) => chain[0][0]);

  expect(new Set(roots).size).toBe(roots.length);
});
