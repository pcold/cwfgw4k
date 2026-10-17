import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreChange, queryKeys } from '@/shared/api/queryKeys';
import type { League, Season, Tournament } from '@/shared/api/types';
import Button from '@/shared/components/Button';
import { SelectField } from '@/shared/components/formFields';
import { mutationError } from '@/shared/util/mutationError';

function ResetTournamentSection(): ReactNode {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: queryKeys.leagues.root, queryFn: api.leagues });
  // No UI to pick a league — just default to the first one.
  const leagueId = leaguesQuery.data?.[0]?.id ?? '';

  const seasonsQuery = useQuery<Season[]>({
    queryKey: queryKeys.seasons.byLeague(leagueId),
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });
  const [userSeasonId, setUserSeasonId] = useState<string>('');
  // User's pick wins; otherwise default to the first loaded season.
  const seasonId = userSeasonId || (seasonsQuery.data?.[0]?.id ?? '');

  const tournamentsQuery = useQuery<Tournament[]>({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === '' ? skipToken : () => api.tournaments(seasonId),
  });

  const [tournamentId, setTournamentId] = useState<string>('');

  const resetMutation = useMutation({
    mutationFn: (id: string) => api.resetTournament(id),
    onSuccess: () => {
      setTournamentId('');
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournaments.root });
      invalidateAfterScoreChange(queryClient, { type: 'everySeason' });
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
    <div className="bg-surface-card rounded-lg p-6">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Reset Tournament Results
      </h3>
      <p className="text-xs text-ink-muted mb-4">
        Delete all scores and results for a finalized tournament, resetting it back to upcoming
        status.
      </p>

      <div className="flex flex-wrap items-end gap-4">
        <SelectField
          id="reset-season"
          label="Season"
          value={seasonId}
          error={undefined}
          placeholder="Select a season"
          options={(seasonsQuery.data ?? []).map((s) => ({ value: s.id, label: `${s.seasonYear} ${s.name}` }))}
          width="w-full sm:w-64"
          onChange={(value) => {
            setUserSeasonId(value);
            setTournamentId('');
          }}
        />
        <SelectField
          id="reset-tournament"
          label="Completed tournament"
          value={tournamentId}
          error={undefined}
          placeholder="-- select completed tournament --"
          options={tournaments.map((t) => ({ value: t.id, label: `Wk ${t.week ?? '?'} — ${t.name}` }))}
          width="w-full sm:w-80"
          onChange={setTournamentId}
        />
        <Button tone="danger" size="regular" onClick={onReset} disabled={resetMutation.isPending || !tournamentId}>
          {resetMutation.isPending ? 'Resetting...' : 'Reset Results'}
        </Button>
      </div>
      {resetMutation.data ? (
        <span className="block mt-3 text-sm text-semantic-success">
          Reset "{resetMutation.data.name}" to {resetMutation.data.status}.
        </span>
      ) : null}
      {errorMessage ? (
        <span role="alert" className="block mt-3 text-sm text-semantic-danger">
          {errorMessage}
        </span>
      ) : null}
    </div>
  );
}

export default ResetTournamentSection;
