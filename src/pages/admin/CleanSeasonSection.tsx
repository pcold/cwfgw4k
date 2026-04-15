import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { League, Season } from '@/api/types';
import { mutationError } from '@/util/mutationError';
import { seasonLabel } from '@/util/season';
import { useDefaultSelectedId } from '@/util/useDefaultSelectedId';

function CleanSeasonSection() {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: ['leagues'], queryFn: api.leagues });
  const [leagueId, setLeagueId] = useState<string>('');
  useDefaultSelectedId(leaguesQuery.data, leagueId, setLeagueId);

  const seasonsQuery = useQuery<Season[]>({
    queryKey: ['seasons', leagueId],
    queryFn: () => api.seasons(leagueId),
    enabled: !!leagueId,
  });

  const [seasonId, setSeasonId] = useState<string>('');

  const cleanMutation = useMutation({
    mutationFn: (id: string) => api.cleanSeasonResults(id),
    onSuccess: () => {
      setSeasonId('');
      void queryClient.invalidateQueries({ queryKey: ['tournaments'] });
      void queryClient.invalidateQueries({ queryKey: ['seasons'] });
    },
  });

  const onClean = () => {
    if (!seasonId) return;
    const selected = seasonsQuery.data?.find((s) => s.id === seasonId);
    const name = selected ? seasonLabel(selected) : seasonId;
    const message =
      `Are you sure you want to clean ALL results for "${name}"?\n\n` +
      'This will delete every score, result, and standing in this season ' +
      'and reset all tournaments to upcoming.';
    if (!window.confirm(message)) return;
    cleanMutation.mutate(seasonId);
  };

  const errorMessage = mutationError(cleanMutation.error);

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Clean All Season Results
      </h3>
      <p className="text-xs text-gray-400 mb-4">
        Delete all scores, results, and standings for an entire season, resetting every
        tournament back to upcoming.
      </p>

      <div className="flex flex-wrap items-center gap-4">
        <select
          aria-label="Season to clean"
          value={seasonId}
          onChange={(e) => setSeasonId(e.target.value)}
          className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-80"
        >
          <option value="">-- select season --</option>
          {(seasonsQuery.data ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {seasonLabel(s)}
            </option>
          ))}
        </select>
        <button
          type="button"
          onClick={onClean}
          disabled={cleanMutation.isPending || !seasonId}
          className="bg-red-600 hover:bg-red-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium whitespace-nowrap"
        >
          {cleanMutation.isPending ? 'Cleaning...' : 'Clean Results'}
        </button>
      </div>
      {cleanMutation.data ? (
        <span className="block mt-3 text-sm text-green-400">{cleanMutation.data.message}</span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-red-400">
          {errorMessage}
        </span>
      ) : null}
    </div>
  );
}

export default CleanSeasonSection;
