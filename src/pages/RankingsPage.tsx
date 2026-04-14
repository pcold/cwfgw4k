import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useLeagueSeason } from '../context/LeagueSeasonContext';
import RankingsView from './RankingsView';

function RankingsPage() {
  const { leagues, leaguesLoading, leaguesError, seasonId, live } = useLeagueSeason();

  const rankingsQuery = useQuery({
    queryKey: ['rankings', seasonId, live],
    queryFn: () => api.rankings(seasonId!, live),
    enabled: !!seasonId,
  });

  if (leaguesLoading) return <p className="text-gray-400">Loading leagues…</p>;
  if (leaguesError)
    return <p className="text-red-400">Failed to load leagues: {String(leaguesError)}</p>;
  if (!leagues || leagues.length === 0)
    return <p className="text-gray-400">No leagues configured.</p>;

  if (rankingsQuery.isLoading) return <p className="text-gray-400">Loading standings…</p>;
  if (rankingsQuery.isError)
    return <p className="text-red-400">Failed to load standings: {String(rankingsQuery.error)}</p>;
  if (!rankingsQuery.data) return null;

  return <RankingsView rankings={rankingsQuery.data} />;
}

export default RankingsPage;
