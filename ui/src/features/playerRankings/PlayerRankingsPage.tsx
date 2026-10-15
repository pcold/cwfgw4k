import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlay } from '@/shared/components/LiveOverlayToggle';
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
  const liveOverlay = useLiveOverlay(
    tournaments ?? [],
    currentQuery.data,
    throughTournamentId || null,
  );

  const playerRankingsQuery = useQuery({
    queryKey: queryKeys.playerRankings.detail(
      seasonId,
      throughTournamentId || null,
      liveOverlay.effectiveLive,
    ),
    queryFn:
      seasonId === null || throughTournamentId === null
        ? skipToken
        : () =>
            api.playerRankings(
              seasonId,
              liveOverlay.effectiveLive,
              throughTournamentId || undefined,
            ),
  });

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
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-xl font-bold">Player Rankings</h2>
          <div className="flex items-center gap-3">
            <label
              htmlFor="player-rankings-through"
              className="text-xs text-ink-muted uppercase tracking-wider"
            >
              Through
            </label>
            <select
              id="player-rankings-through"
              className="bg-surface-card border border-control-border rounded px-3 py-2 text-sm"
              value={throughTournamentId}
              onChange={(e) => setThroughOverride(e.target.value)}
            >
              <option value={ALL_TOURNAMENTS}>All Tournaments</option>
              {tournaments.map((t) => (
                <option key={t.id} value={t.id}>
                  {tournamentLabel(t)}
                </option>
              ))}
            </select>
            <LiveOverlayCheckbox state={liveOverlay} id="player-rankings-live-overlay" />
          </div>
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
