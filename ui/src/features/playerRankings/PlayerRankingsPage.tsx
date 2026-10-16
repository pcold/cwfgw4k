import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import PickerRow from '@/shared/components/PickerRow';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import PlayerRankingsView from './PlayerRankingsView';

function PlayerRankingsPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [throughOverride, setThroughOverride] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

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
  const { query: playerRankingsQuery, overlay: liveOverlay } = useLiveOverlayQuery(
    (live) => queryKeys.playerRankings.detail(seasonId, throughTournamentId || null, live),
    seasonId === null || throughTournamentId === null
      ? null
      : (live) => api.playerRankings(seasonId, live, throughTournamentId || undefined),
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
    <>
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-xl font-bold">Player Rankings</h2>
          <PickerRow
            id="player-rankings-through"
            label="Through"
            value={throughTournamentId}
            options={tournamentOptions}
            onChange={setThroughOverride}
          >
            <LiveOverlayCheckbox state={liveOverlay} id="player-rankings-live-overlay" />
          </PickerRow>
        </div>

        {tournamentsQuery.isError ? (
          <p className="text-semantic-danger">
            Failed to load tournaments: {String(tournamentsQuery.error)}
          </p>
        ) : playerRankingsQuery.isError ? (
          <p className="text-semantic-danger">
            Failed to load player rankings: {String(playerRankingsQuery.error)}
          </p>
        ) : playerRankingsQuery.isLoading ? (
          <p className="text-ink-muted">Loading player rankings…</p>
        ) : (
          <PlayerRankingsView
            players={playerRankingsQuery.data?.players ?? []}
            live={playerRankingsQuery.data?.live ?? false}
            onGolferClick={setHistoryGolferId}
          />
        )}
      </div>
      <GolferHistoryModal
        seasonId={seasonId}
        golferId={historyGolferId}
        onClose={() => setHistoryGolferId(null)}
      />
    </>
  );
}

export default PlayerRankingsPage;
