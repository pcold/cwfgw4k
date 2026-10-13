import { useState } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import RostersView from './RostersView';

function RostersPage() {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

  const rostersQuery = useQuery({
    queryKey: queryKeys.rosters.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.rosters(seasonId),
  });

  const rulesQuery = useQuery({
    queryKey: queryKeys.seasonRules.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.seasonRules(seasonId),
  });
  // Only shades rows, so the grid neither waits on the rules nor fails with them.
  const sideBetRounds = rulesQuery.data?.sideBetRounds ?? [];

  if (leaguesGate) return leaguesGate;

  return (
    <>
      <QueryState query={rostersQuery} label="rosters">
        {(teams) => (
          <RostersView
            teams={teams}
            sideBetRounds={sideBetRounds}
            onGolferClick={setHistoryGolferId}
          />
        )}
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
