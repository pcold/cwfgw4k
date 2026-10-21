import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreChange, queryKeys } from '@/shared/api/queryKeys';
import type { League, Season } from '@/shared/api/types';
import Button from '@/shared/components/Button';
import { SelectField } from '@/shared/components/formFields';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';
import AdminSection from './AdminSection';

function CleanSeasonSection(): ReactNode {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: queryKeys.leagues.root, queryFn: api.leagues });
  // No UI to pick a league — just default to the first one when leagues load.
  const leagueId = leaguesQuery.data?.[0]?.id ?? '';

  const seasonsQuery = useQuery<Season[]>({
    queryKey: queryKeys.seasons.byLeague(leagueId),
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });

  const [seasonId, setSeasonId] = useState<string>('');

  const cleanMutation = useMutation({
    mutationFn: (id: string) => api.cleanSeasonResults(id),
    onSuccess: () => {
      setSeasonId('');
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournaments.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.seasons.root });
      invalidateAfterScoreChange(queryClient, { type: 'everySeason' });
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
    <AdminSection
      title="Clean All Season Results"
      description={
        <>
          Delete all scores, results, and standings for an entire season, resetting every
          tournament back to upcoming.
        </>
      }
    >
      <div className="flex flex-wrap items-end gap-4">
        <SelectField
          id="clean-season"
          label="Season to clean"
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
          onClick={onClean}
          disabled={cleanMutation.isPending || !seasonId}
        >
          {cleanMutation.isPending ? 'Cleaning...' : 'Clean Results'}
        </Button>
      </div>
      {cleanMutation.data ? (
        <span className="block mt-3 text-sm text-semantic-success">
          Cleaned {cleanMutation.data.scoresDeleted} scores,{' '}
          {cleanMutation.data.resultsDeleted} results; reset{' '}
          {cleanMutation.data.tournamentsReset} tournaments.
        </span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-semantic-danger">
          {errorMessage}
        </span>
      ) : null}
    </AdminSection>
  );
}

export default CleanSeasonSection;
