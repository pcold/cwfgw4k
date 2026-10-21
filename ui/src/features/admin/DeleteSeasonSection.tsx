import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreChange, queryKeys } from '@/shared/api/queryKeys';
import type { Season } from '@/shared/api/types';
import Button from '@/shared/components/Button';
import SelectField from '@/shared/components/SelectField';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';
import AdminLeagueField from './AdminLeagueField';
import { useAdminLeague } from './useAdminLeague';
import AdminSection from './AdminSection';

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
    onSuccess: (_data, deletedSeasonId) => {
      setSeasonId('');
      void queryClient.invalidateQueries({ queryKey: queryKeys.seasons.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournaments.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.leagues.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dataHealth.root });
      // A season's scores live only in its own tournaments, so no other season's report moves.
      invalidateAfterScoreChange(queryClient, { type: 'season', seasonId: deletedSeasonId });
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
    <AdminSection
      title="Delete Season"
      description={
        <>
          Permanently delete a season along with its schedule, drafted teams, rosters, picks,
          scores, and standings. This cannot be undone.
        </>
      }
    >
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
        <Button
          tone="danger"
          size="regular"
          layout="whitespace-nowrap"
          onClick={onDelete}
          disabled={deleteMutation.isPending || !seasonId}
        >
          {deleteMutation.isPending ? 'Deleting...' : 'Delete Season'}
        </Button>
      </div>
      {deleteMutation.isSuccess ? (
        <span className="block mt-3 text-sm text-semantic-success">Season deleted.</span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-semantic-danger">
          {errorMessage}
        </span>
      ) : null}
    </AdminSection>
  );
}

export default DeleteSeasonSection;
