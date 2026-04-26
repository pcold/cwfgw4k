import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { League, Season } from '@/api/types';
import { mutationError } from '@/util/mutationError';
import { seasonLabel } from '@/util/season';
import { useDefaultSelectedId } from '@/util/useDefaultSelectedId';

function DeleteSeasonSection() {
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

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteSeason(id),
    onSuccess: () => {
      setSeasonId('');
      void queryClient.invalidateQueries({ queryKey: ['seasons'] });
      void queryClient.invalidateQueries({ queryKey: ['tournaments'] });
      void queryClient.invalidateQueries({ queryKey: ['leagues'] });
    },
  });

  const onDelete = () => {
    if (!seasonId) return;
    const selected = seasonsQuery.data?.find((s) => s.id === seasonId);
    const name = selected ? seasonLabel(selected) : seasonId;
    const message =
      `Are you sure you want to DELETE the season "${name}"?\n\n` +
      'This will permanently remove the season, its schedule (tournaments), ' +
      'every drafted team and roster, drafts, picks, scores, and standings. ' +
      'This cannot be undone.';
    if (!window.confirm(message)) return;
    deleteMutation.mutate(seasonId);
  };

  const errorMessage = mutationError(deleteMutation.error);

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Delete Season
      </h3>
      <p className="text-xs text-gray-400 mb-4">
        Permanently delete a season along with its schedule, drafted teams, rosters, drafts,
        picks, scores, and standings. This cannot be undone.
      </p>

      <div className="flex flex-wrap items-center gap-4">
        <select
          aria-label="Season to delete"
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
          onClick={onDelete}
          disabled={deleteMutation.isPending || !seasonId}
          className="bg-red-600 hover:bg-red-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium whitespace-nowrap"
        >
          {deleteMutation.isPending ? 'Deleting...' : 'Delete Season'}
        </button>
      </div>
      {deleteMutation.data ? (
        <span className="block mt-3 text-sm text-green-400">{deleteMutation.data.message}</span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-red-400">
          {errorMessage}
        </span>
      ) : null}
    </div>
  );
}

export default DeleteSeasonSection;
