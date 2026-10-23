import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { useMyTeam } from '@/features/myTeam/MyTeamContext';
import { chosenTeamName } from '@/features/myTeam/myTeamModel';
import QueryState from '@/shared/components/QueryState';
import { useLeaguesGate } from '@/shared/components/useLeaguesGate';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import PageTitle from '@/shared/components/PageTitle';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import PhonePicker, { DESKTOP_PICKER_CLASS } from '@/features/leagues/PhonePicker';
import RostersView from './RostersView';

function RostersPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const myTeamName = chosenTeamName(useMyTeam().choice);
  const leaguesGate = useLeaguesGate();
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);
  const [selectedTeamId, setSelectedTeamId] = useState<string | null>(null);

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

  return (
    <div className="space-y-4">
      <PageTitle
        title="Team Rosters"
        actions={
          <>
            <div className={DESKTOP_PICKER_CLASS}>
              <LeagueSeasonPicker />
            </div>
            <PhonePicker />
          </>
        }
      />
      {leaguesGate ?? (
        <QueryState query={rostersQuery} label="rosters">
          {(teams) => (
            <RostersView
              teams={teams}
              sideBetRounds={sideBetRounds}
              selectedTeamId={selectedTeamId}
              myTeamName={myTeamName}
              onSelectTeam={setSelectedTeamId}
              onGolferClick={setHistoryGolferId}
            />
          )}
        </QueryState>
      )}
      <GolferHistoryModal seasonId={seasonId} golferId={historyGolferId} onClose={() => setHistoryGolferId(null)} />
    </div>
  );
}

export default RostersPage;
