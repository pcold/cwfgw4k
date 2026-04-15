import { useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import { useLeaguesGate } from '@/components/QueryState';
import GolferHistoryModal from '@/components/GolferHistoryModal';
import { tournamentLabel } from '@/util/tournament';
import type { Tournament, WeeklyReport } from '@/api/types';
import { buildPlayerRankings } from './playerRankingsModel';
import PlayerRankingsView from './PlayerRankingsView';

const ALL_TOURNAMENTS = '';

interface FetchTarget {
  id: string;
  useLive: boolean;
}

function tournamentsToFetch(
  tournaments: Tournament[],
  throughId: string,
  live: boolean,
): FetchTarget[] {
  const completed = tournaments.filter((t) => t.status === 'completed');
  const inProgress = tournaments.filter((t) => t.status !== 'completed');

  let candidates: { tournament: Tournament; useLive: boolean }[] = [
    ...completed.map((t) => ({ tournament: t, useLive: false })),
    ...(live ? inProgress.map((t) => ({ tournament: t, useLive: true })) : []),
  ];

  if (throughId) {
    const through = tournaments.find((t) => t.id === throughId);
    if (!through) return [];
    candidates = candidates.filter(
      (c) => c.tournament.startDate <= through.startDate,
    );
    if (live && through.status !== 'completed') {
      const alreadyIncluded = candidates.some((c) => c.tournament.id === throughId);
      if (!alreadyIncluded) {
        candidates.push({ tournament: through, useLive: true });
      }
    }
  }

  return candidates
    .sort((a, b) => a.tournament.startDate.localeCompare(b.tournament.startDate))
    .map((c) => ({ id: c.tournament.id, useLive: c.useLive }));
}

function PlayerRankingsPage() {
  const { seasonId, live } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [throughTournamentId, setThroughTournamentId] = useState<string>(ALL_TOURNAMENTS);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: () => api.tournaments(seasonId!),
    enabled: !!seasonId,
  });

  const rostersQuery = useQuery({
    queryKey: ['rosters', seasonId],
    queryFn: () => api.rosters(seasonId!),
    enabled: !!seasonId,
  });

  const tournaments = tournamentsQuery.data ?? [];
  const targets = tournamentsToFetch(tournaments, throughTournamentId, live);

  const reportQueries = useQueries({
    queries: targets.map((target) => ({
      queryKey: ['report', seasonId, target.id, target.useLive],
      queryFn: () => api.tournamentReport(seasonId!, target.id, target.useLive),
      enabled: !!seasonId,
    })),
  });

  if (leaguesGate) return leaguesGate;

  const tournamentsLoading = tournamentsQuery.isLoading || rostersQuery.isLoading;
  const reportsLoading = reportQueries.some((q) => q.isLoading);
  const reportsError = reportQueries.find((q) => q.isError);

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
              onChange={(e) => setThroughTournamentId(e.target.value)}
            >
              <option value={ALL_TOURNAMENTS}>All Tournaments</option>
              {tournaments.map((t) => (
                <option key={t.id} value={t.id}>
                  {tournamentLabel(t)}
                </option>
              ))}
            </select>
          </div>
        </div>

        {tournamentsQuery.isError ? (
          <p className="text-red-400">
            Failed to load tournaments: {String(tournamentsQuery.error)}
          </p>
        ) : rostersQuery.isError ? (
          <p className="text-red-400">
            Failed to load rosters: {String(rostersQuery.error)}
          </p>
        ) : reportsError ? (
          <p className="text-red-400">
            Failed to load reports: {String(reportsError.error)}
          </p>
        ) : tournamentsLoading || reportsLoading ? (
          <p className="text-gray-400">Loading player rankings…</p>
        ) : (
          (() => {
            const reports = reportQueries
              .map((q) => q.data)
              .filter((r): r is WeeklyReport => !!r);
            const players = buildPlayerRankings(reports, rostersQuery.data ?? []);
            const liveOverlay = targets.some((t) => t.useLive);
            return (
              <PlayerRankingsView
                players={players}
                live={liveOverlay}
                onGolferClick={setHistoryGolferId}
              />
            );
          })()
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
