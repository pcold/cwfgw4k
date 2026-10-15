import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { Season } from '@/shared/api/types';
import { SelectField } from '@/shared/components/formFields';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';
import { AdminLeagueField, useAdminLeague } from './AdminLeagueField';

const DELETE_BUTTON_CLASS =
  'bg-semantic-danger-fill hover:bg-semantic-danger-fill-hover disabled:bg-disabled text-on-fill ' +
  'px-4 py-2 rounded text-sm font-medium whitespace-nowrap';

function DeleteSeasonSection(): ReactNode {
  const queryClient = useQueryClient();

  const { leagues, leagueId, setLeagueId } = useAdminLeague();

  const seasonsQuery = useQuery<Season[]>({
    queryKey: queryKeys.seasons.byLeague(leagueId),
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });

  const [seasonId, setSeasonId] = useState<string>('');

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteSeason(id),
    onSuccess: () => {
      setSeasonId('');
      void queryClient.invalidateQueries({ queryKey: queryKeys.seasons.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournaments.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.leagues.root });
    },
  });

  const onDelete = () => {
    if (!seasonId) return;
    const selected = seasonsQuery.data?.find((s) => s.id === seasonId);
    const name = selected ? seasonLabel(selected) : seasonId;
    const message =
      `Are you sure you want to DELETE the season "${name}"?\n\n` +
      'This will permanently remove the season, its schedule (tournaments), ' +
      'every drafted team and roster, picks, scores, and standings. ' +
      'This cannot be undone.';
    if (!window.confirm(message)) return;
    deleteMutation.mutate(seasonId);
  };

  const errorMessage = mutationError(deleteMutation.error);

  return (
    <div className="bg-surface-card rounded-lg p-6">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Delete Season
      </h3>
      <p className="text-xs text-ink-muted mb-4">
        Permanently delete a season along with its schedule, drafted teams, rosters, picks,
        scores, and standings. This cannot be undone.
      </p>

      <div className="flex flex-wrap items-end gap-4">
        <AdminLeagueField
          id="delete-season-league"
          leagues={leagues}
          leagueId={leagueId}
          onChange={(id) => {
            setLeagueId(id);
            setSeasonId('');
          }}
        />
        <SelectField
          id="delete-season"
          label="Season to delete"
          value={seasonId}
          error={undefined}
          placeholder="-- select season --"
          options={(seasonsQuery.data ?? []).map((s) => ({ value: s.id, label: seasonLabel(s) }))}
          width="w-full sm:w-80"
          onChange={setSeasonId}
        />
        <button
          type="button"
          onClick={onDelete}
          disabled={deleteMutation.isPending || !seasonId}
          className={DELETE_BUTTON_CLASS}
        >
          {deleteMutation.isPending ? 'Deleting...' : 'Delete Season'}
        </button>
      </div>
      {deleteMutation.isSuccess ? (
        <span className="block mt-3 text-sm text-semantic-success">Season deleted.</span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-semantic-danger">
          {errorMessage}
        </span>
      ) : null}
    </div>
  );
}

export default DeleteSeasonSection;
