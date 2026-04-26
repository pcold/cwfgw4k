import { useEffect, useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { useAuth } from '@/features/auth/AuthContext';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { mutationError } from '@/shared/util/mutationError';
import { earliestUnfinalized, tournamentLabel } from '@/shared/util/tournament';
import type { WeeklyReport } from '@/shared/api/types';
import ScoreboardView from './ScoreboardView';

function ScoreboardPage() {
  const { seasonId, live } = useLeagueSeason();
  const { authenticated } = useAuth();
  const leaguesGate = useLeaguesGate();
  const queryClient = useQueryClient();
  const [tournamentId, setTournamentId] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  const tournaments = tournamentsQuery.data ?? [];

  useEffect(() => {
    if (tournaments.length === 0) {
      if (tournamentId !== null) setTournamentId(null);
      return;
    }
    if (!tournamentId || !tournaments.some((t) => t.id === tournamentId)) {
      setTournamentId(earliestUnfinalized(tournaments));
    }
  }, [tournaments, tournamentId]);

  const reportQuery = useQuery({
    queryKey: ['tournamentReport', seasonId, tournamentId, live],
    queryFn:
      seasonId === null || tournamentId === null
        ? skipToken
        : () => api.tournamentReport(seasonId, tournamentId, live),
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
    if (report.tournament.status === 'completed') return null;
    return (
      <div className="flex items-center gap-3">
        {finalizeErr ? (
          <span role="alert" className="text-red-300 text-xs">
            {finalizeErr}
          </span>
        ) : null}
        <button
          type="button"
          onClick={() => finalizeMutation.mutate(tournamentId)}
          disabled={finalizeMutation.isPending}
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-3 py-1 rounded text-xs font-medium whitespace-nowrap"
        >
          {finalizeMutation.isPending ? 'Finalizing…' : 'Finalize Results'}
        </button>
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
                onChange={(e) => setTournamentId(e.target.value)}
              >
                {loadedTournaments.map((t) => (
                  <option key={t.id} value={t.id}>
                    {tournamentLabel(t)}
                  </option>
                ))}
              </select>
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
          </div>
        );
      }}
    </QueryState>
  );
}

export default ScoreboardPage;
