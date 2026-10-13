import { expect, it } from 'vitest';
import { QueryClient, type QueryKey } from '@tanstack/react-query';
import { queryKeys } from './queryKeys';

// Widest key first. The Record makes an entity added without a chain a compile error, since nothing
// below would check it.
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
    queryKeys.playerRankings.detail('sn-1', 'tn-1', true),
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
      ['player-rankings', 'sn-1', 'tn-1', true],
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

it('gives every entity a root of its own, so no invalidation can reach another entity', () => {
  const roots = Object.values(KEY_CHAINS).map((chain) => chain[0][0]);

  expect(new Set(roots).size).toBe(roots.length);
});
