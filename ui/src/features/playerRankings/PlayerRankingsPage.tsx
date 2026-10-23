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
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import PageTitle from '@/shared/components/PageTitle';
import PickerRow from '@/shared/components/PickerRow';
import StatusChip, { statusChipVariant } from '@/shared/components/StatusChip';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import PhonePicker, { DESKTOP_PICKER_CLASS } from '@/features/leagues/PhonePicker';
import type { Tournament } from '@/shared/api/types';
import { ALL_TOURNAMENTS, defaultReportSelection, pickInSeason, tournamentLabel } from '@/shared/util/tournament';
import PlayerRankingsView from './PlayerRankingsView';

function PlayerRankingsPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const myTeamName = chosenTeamName(useMyTeam().choice);
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
  const {
    query: playerRankingsQuery,
    overlay: liveOverlay,
    selectionKey,
  } = useLiveOverlayQuery(
    (live) => queryKeys.playerRankings.detail(seasonId, throughTournamentId || null, live),
    seasonId === null || throughTournamentId === null
      ? null
      : (live) => api.playerRankings(seasonId, live, throughTournamentId || undefined),
  );

  const playerRankings = playerRankingsQuery.data;
  const qualifyingCount = playerRankings?.players.length ?? 0;

  function renderThroughField(loaded: readonly Tournament[], selectedId: string, idSuffix: string): ReactNode {
    const tournamentOptions = [
      { value: ALL_TOURNAMENTS, label: 'All Tournaments' },
      ...loaded.map((t) => ({ value: t.id, label: tournamentLabel(t) })),
    ];

    return (
      <PickerRow
        id={`player-rankings-through${idSuffix}`}
        label="Through"
        value={selectedId}
        options={tournamentOptions}
        onChange={setThroughOverride}
      >
        <LiveOverlayCheckbox state={liveOverlay} id={`player-rankings-live-overlay${idSuffix}`} />
      </PickerRow>
    );
  }

  function renderBody(): ReactNode {
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

    return (
      <>
        <div className={DESKTOP_PICKER_CLASS}>{renderThroughField(tournaments, throughTournamentId, '')}</div>

        {tournamentsQuery.isError ? (
          <p className="text-semantic-danger">Failed to load tournaments: {String(tournamentsQuery.error)}</p>
        ) : playerRankingsQuery.isError ? (
          <p className="text-semantic-danger">Failed to load player rankings: {String(playerRankingsQuery.error)}</p>
        ) : playerRankingsQuery.isLoading ? (
          <p className="text-ink-muted">Loading player rankings…</p>
        ) : (
          <PlayerRankingsView
            players={playerRankings?.players ?? []}
            myTeamName={myTeamName}
            onGolferClick={setHistoryGolferId}
            selectionKey={selectionKey}
          />
        )}
      </>
    );
  }

  return (
    <>
      <div className="space-y-4">
        <PageTitle
          title="Player Rankings"
          subtitle={qualifyingCount > 0 ? `${qualifyingCount} players with at least one top 10` : undefined}
          actions={
            <>
              <StatusChip variant={statusChipVariant(playerRankings)} />
              <div className={DESKTOP_PICKER_CLASS}>
                <LeagueSeasonPicker />
              </div>
              <PhonePicker
                tournament={tournaments?.find((t) => t.id === throughTournamentId)}
                renderField={
                  tournaments === undefined || throughTournamentId === null
                    ? undefined
                    : (idSuffix) => renderThroughField(tournaments, throughTournamentId, idSuffix)
                }
              />
            </>
          }
        />
        {renderBody()}
      </div>
      <GolferHistoryModal seasonId={seasonId} golferId={historyGolferId} onClose={() => setHistoryGolferId(null)} />
    </>
  );
}

export default PlayerRankingsPage;
