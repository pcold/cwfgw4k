import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import RostersView from './RostersView';

function RostersPage() {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

  const rostersQuery = useQuery({
    queryKey: ['rosters', seasonId],
    queryFn: () => api.rosters(seasonId!),
    enabled: !!seasonId,
  });

  if (leaguesGate) return leaguesGate;

  return (
    <>
      <QueryState query={rostersQuery} label="rosters">
        {(teams) => <RostersView teams={teams} onGolferClick={setHistoryGolferId} />}
      </QueryState>
      <GolferHistoryModal
        seasonId={seasonId}
        golferId={historyGolferId}
        onClose={() => setHistoryGolferId(null)}
      />
    </>
  );
}

export default RostersPage;
