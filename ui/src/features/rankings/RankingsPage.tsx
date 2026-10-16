import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import PickerRow from '@/shared/components/PickerRow';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import RankingsView from './RankingsView';

function RankingsPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [throughOverride, setThroughOverride] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });
  const currentQuery = useQuery({
    queryKey: queryKeys.tournaments.current(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.currentTournament(seasonId),
  });

  const tournaments = tournamentsQuery.data;
  const throughTournamentId =
    tournaments === undefined
      ? null
      : (pickInSeason(throughOverride, tournaments) ??
        pickInSeason(defaultReportSelection(currentQuery.data), tournaments));

  const { query: rankingsQuery, overlay: liveOverlay } = useLiveOverlayQuery(
    (live) => queryKeys.rankings.detail(seasonId, live, throughTournamentId),
    seasonId === null || throughTournamentId === null
      ? null
      : (live) => api.rankings(seasonId, live, throughTournamentId || undefined),
  );

  if (leaguesGate) return leaguesGate;
  if (tournaments === undefined || throughTournamentId === null) {
    return (
      <QueryState query={tournamentsQuery} label="tournaments">
        {() => (
          <QueryState query={currentQuery} label="current tournament">
            {() => null}
          </QueryState>
        )}
      </QueryState>
    );
  }

  const tournamentOptions = [
    { value: ALL_TOURNAMENTS, label: 'All Tournaments' },
    ...tournaments.map((t) => ({ value: t.id, label: tournamentLabel(t) })),
  ];

  return (
    <div className="space-y-4">
      <PickerRow
        id="rankings-through-tournament"
        label="Through"
        value={throughTournamentId}
        options={tournamentOptions}
        onChange={setThroughOverride}
      >
        <LiveOverlayCheckbox state={liveOverlay} id="rankings-live-overlay" />
      </PickerRow>

      <QueryState query={rankingsQuery} label="standings">
        {(rankings) => <RankingsView rankings={rankings} />}
      </QueryState>
    </div>
  );
}

export default RankingsPage;
