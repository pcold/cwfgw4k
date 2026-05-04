import { useState } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlay } from '@/shared/components/LiveOverlayToggle';
import { earliestUnfinalized, tournamentLabel } from '@/shared/util/tournament';
import PlayerRankingsView from './PlayerRankingsView';

const ALL_TOURNAMENTS = '';

function PlayerRankingsPage() {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  // null = use the computed default (earliest non-finalized tournament, or
  // ALL_TOURNAMENTS if every tournament is completed).
  const [throughOverride, setThroughOverride] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  const tournaments = tournamentsQuery.data ?? [];
  const defaultThrough =
    tournamentsQuery.data === undefined
      ? null
      : (earliestUnfinalized(tournamentsQuery.data) ?? ALL_TOURNAMENTS);
  const throughTournamentId = throughOverride ?? defaultThrough ?? ALL_TOURNAMENTS;
  const liveOverlay = useLiveOverlay(tournaments, throughTournamentId || null);

  const playerRankingsQuery = useQuery({
    queryKey: [
      'player-rankings',
      seasonId,
      throughTournamentId || null,
      liveOverlay.effectiveLive,
    ],
    queryFn:
      seasonId === null
        ? skipToken
        : () =>
            api.playerRankings(
              seasonId,
              liveOverlay.effectiveLive,
              throughTournamentId || undefined,
            ),
  });

  if (leaguesGate) return leaguesGate;

  return (
    <>
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-xl font-bold">Player Rankings</h2>
          <div className="flex items-center gap-3">
            <label
              htmlFor="player-rankings-through"
              className="text-xs text-gray-400 uppercase tracking-wider"
            >
              Through
            </label>
            <select
              id="player-rankings-through"
              className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm"
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
          <p className="text-red-400">
            Failed to load tournaments: {String(tournamentsQuery.error)}
          </p>
        ) : playerRankingsQuery.isError ? (
          <p className="text-red-400">
            Failed to load player rankings: {String(playerRankingsQuery.error)}
          </p>
        ) : tournamentsQuery.isLoading || playerRankingsQuery.isLoading ? (
          <p className="text-gray-400">Loading player rankings…</p>
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
