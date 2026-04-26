import { useState } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type { League, Season, Tournament } from '@/shared/api/types';
import { mutationError } from '@/shared/util/mutationError';

function ResetTournamentSection() {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: ['leagues'], queryFn: api.leagues });
  // No UI to pick a league — just default to the first one.
  const leagueId = leaguesQuery.data?.[0]?.id ?? '';

  const seasonsQuery = useQuery<Season[]>({
    queryKey: ['seasons', leagueId],
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });
  const [userSeasonId, setUserSeasonId] = useState<string>('');
  // User's pick wins; otherwise default to the first loaded season.
  const seasonId = userSeasonId || (seasonsQuery.data?.[0]?.id ?? '');

  const tournamentsQuery = useQuery<Tournament[]>({
    queryKey: ['tournaments', seasonId],
    queryFn: seasonId === '' ? skipToken : () => api.tournaments(seasonId),
  });

  const [tournamentId, setTournamentId] = useState<string>('');

  const resetMutation = useMutation({
    mutationFn: (id: string) => api.resetTournament(id),
    onSuccess: () => {
      setTournamentId('');
      void queryClient.invalidateQueries({ queryKey: ['tournaments'] });
    },
  });

  const tournaments = (tournamentsQuery.data ?? []).filter((t) => t.status === 'completed');

  const onReset = () => {
    if (!tournamentId) return;
    const selected = tournaments.find((t) => t.id === tournamentId);
    const name = selected?.name ?? tournamentId;
    const message =
      `Are you sure you want to reset "${name}"?\n\n` +
      'This will delete all scores and results for this tournament ' +
      'and set it back to upcoming.';
    if (!window.confirm(message)) return;
    resetMutation.mutate(tournamentId);
  };

  const errorMessage = mutationError(resetMutation.error);

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Reset Tournament Results
      </h3>
      <p className="text-xs text-gray-400 mb-4">
        Delete all scores and results for a finalized tournament, resetting it back to upcoming
        status.
      </p>

      <div className="flex flex-wrap items-center gap-4">
        <select
          aria-label="Season"
          value={seasonId}
          onChange={(e) => {
            setUserSeasonId(e.target.value);
            setTournamentId('');
          }}
          className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-64"
        >
          <option value="" disabled>
            Select a season
          </option>
          {(seasonsQuery.data ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.seasonYear} {s.name}
            </option>
          ))}
        </select>
        <select
          aria-label="Completed tournament"
          value={tournamentId}
          onChange={(e) => setTournamentId(e.target.value)}
          className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-80"
        >
          <option value="">-- select completed tournament --</option>
          {tournaments.map((t) => (
            <option key={t.id} value={t.id}>
              Wk {t.week ?? '?'} — {t.name}
            </option>
          ))}
        </select>
        <button
          type="button"
          onClick={onReset}
          disabled={resetMutation.isPending || !tournamentId}
          className="bg-red-600 hover:bg-red-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {resetMutation.isPending ? 'Resetting...' : 'Reset Results'}
        </button>
      </div>
      {resetMutation.data ? (
        <span className="block mt-3 text-sm text-green-400">{resetMutation.data.message}</span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-red-400">
          {errorMessage}
        </span>
      ) : null}
    </div>
  );
}

export default ResetTournamentSection;
