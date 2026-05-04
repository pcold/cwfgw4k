import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { useAuth } from '@/features/auth/AuthContext';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlay } from '@/shared/components/LiveOverlayToggle';
import { mutationError } from '@/shared/util/mutationError';
import { defaultScoreboardTournament, tournamentLabel } from '@/shared/util/tournament';
import type { WeeklyReport } from '@/shared/api/types';
import PlayerLinksPanel from './PlayerLinksPanel';
import ScoreboardView from './ScoreboardView';

function ScoreboardPage() {
  const { seasonId } = useLeagueSeason();
  const { authenticated, isAdmin } = useAuth();
  const leaguesGate = useLeaguesGate();
  const queryClient = useQueryClient();
  const [userTournamentId, setUserTournamentId] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);
  const [linksPanelOpen, setLinksPanelOpen] = useState(false);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  const tournaments = tournamentsQuery.data ?? [];

  // User's explicit pick wins if it's still in the loaded set; otherwise
  // defaultScoreboardTournament picks the right one (earliest unfinalized,
  // or the most recent if everything is completed). Derived rather than
  // synced via effect so season changes don't need a separate handler.
  const tournamentId =
    userTournamentId !== null && tournaments.some((t) => t.id === userTournamentId)
      ? userTournamentId
      : defaultScoreboardTournament(tournaments);

  const liveOverlay = useLiveOverlay(tournaments, tournamentId);

  const reportQuery = useQuery({
    queryKey: ['tournamentReport', seasonId, tournamentId, liveOverlay.effectiveLive],
    queryFn:
      seasonId === null || tournamentId === null
        ? skipToken
        : () => api.tournamentReport(seasonId, tournamentId, liveOverlay.effectiveLive),
  });

  const finalizeMutation = useMutation({
    mutationFn: (id: string) => api.finalizeTournament(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tournaments', seasonId] });
      void queryClient.invalidateQueries({ queryKey: ['tournamentReport', seasonId] });
      void queryClient.invalidateQueries({ queryKey: ['rankings', seasonId] });
      void queryClient.invalidateQueries({ queryKey: ['seasonReport', seasonId] });
    },
  });
  const finalizeErr = mutationError(finalizeMutation.error);

  function buildFinalizeSlot(report: WeeklyReport): ReactNode {
    if (!authenticated || !tournamentId) return null;
    const showLinks = isAdmin;
    const showFinalize = isAdmin && report.tournament.status !== 'completed';
    if (!showLinks && !showFinalize) return null;
    return (
      <div className="flex items-center gap-3">
        {finalizeErr ? (
          <span role="alert" className="text-red-300 text-xs">
            {finalizeErr}
          </span>
        ) : null}
        {showLinks ? (
          <button
            type="button"
            onClick={() => setLinksPanelOpen(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-xs font-medium whitespace-nowrap"
          >
            Manage player links
          </button>
        ) : null}
        {showFinalize ? (
          <button
            type="button"
            onClick={() => finalizeMutation.mutate(tournamentId)}
            disabled={finalizeMutation.isPending}
            className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-3 py-1 rounded text-xs font-medium whitespace-nowrap"
          >
            {finalizeMutation.isPending ? 'Finalizing…' : 'Finalize Results'}
          </button>
        ) : null}
      </div>
    );
  }

  if (leaguesGate) return leaguesGate;

  return (
    <QueryState query={tournamentsQuery} label="tournaments">
      {(loadedTournaments) => {
        if (loadedTournaments.length === 0) {
          return <p className="text-gray-400">No tournaments scheduled for this season.</p>;
        }
        return (
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <label
                htmlFor="tournament-select"
                className="text-xs text-gray-400 uppercase tracking-wider"
              >
                Tournament
              </label>
              <select
                id="tournament-select"
                className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm"
                value={tournamentId ?? ''}
                onChange={(e) => setUserTournamentId(e.target.value)}
              >
                {loadedTournaments.map((t) => (
                  <option key={t.id} value={t.id}>
                    {tournamentLabel(t)}
                  </option>
                ))}
              </select>
              <LiveOverlayCheckbox state={liveOverlay} id="scoreboard-live-overlay" />
            </div>

            <QueryState query={reportQuery} label="scoreboard">
              {(report) => (
                <ScoreboardView
                  report={report}
                  finalizeSlot={buildFinalizeSlot(report)}
                  onGolferClick={setHistoryGolferId}
                />
              )}
            </QueryState>
            <GolferHistoryModal
              seasonId={seasonId}
              golferId={historyGolferId}
              onClose={() => setHistoryGolferId(null)}
            />
            {linksPanelOpen && isAdmin && tournamentId && seasonId ? (
              <PlayerLinksPanel
                tournamentId={tournamentId}
                seasonId={seasonId}
                onClose={() => setLinksPanelOpen(false)}
              />
            ) : null}
          </div>
        );
      }}
    </QueryState>
  );
}

export default ScoreboardPage;
