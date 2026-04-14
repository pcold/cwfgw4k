import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useLeagueSeason } from '../context/LeagueSeasonContext';
import RostersView from './RostersView';

function RostersPage() {
  const { leagues, leaguesLoading, leaguesError, seasonId } = useLeagueSeason();

  const rostersQuery = useQuery({
    queryKey: ['rosters', seasonId],
    queryFn: () => api.rosters(seasonId!),
    enabled: !!seasonId,
  });

  if (leaguesLoading) return <p className="text-gray-400">Loading leagues…</p>;
  if (leaguesError)
    return <p className="text-red-400">Failed to load leagues: {String(leaguesError)}</p>;
  if (!leagues || leagues.length === 0)
    return <p className="text-gray-400">No leagues configured.</p>;

  if (rostersQuery.isLoading) return <p className="text-gray-400">Loading rosters…</p>;
  if (rostersQuery.isError)
    return <p className="text-red-400">Failed to load rosters: {String(rostersQuery.error)}</p>;
  if (!rostersQuery.data) return null;

  return <RostersView teams={rostersQuery.data} />;
}

export default RostersPage;
