import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/components/QueryState';
import RostersView from './RostersView';

function RostersPage() {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();

  const rostersQuery = useQuery({
    queryKey: ['rosters', seasonId],
    queryFn: () => api.rosters(seasonId!),
    enabled: !!seasonId,
  });

  if (leaguesGate) return leaguesGate;

  return (
    <QueryState query={rostersQuery} label="rosters">
      {(teams) => <RostersView teams={teams} />}
    </QueryState>
  );
}

export default RostersPage;
