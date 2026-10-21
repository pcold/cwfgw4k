import { useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { Season, SeasonRules } from '@/shared/api/types';
import Button from '@/shared/components/Button';
import FieldShell from '@/shared/components/FieldShell';
import TextField from '@/shared/components/TextField';
import { CONTROL_CLASS, FIELD_CLASS, TAP_TARGET_CLASS } from '@/shared/components/formControls';
import QueryState from '@/shared/components/QueryState';
import { mutationError } from '@/shared/util/mutationError';
import AdminLeagueField from './AdminLeagueField';
import { useAdminLeague } from './useAdminLeague';
import AdminSection from './AdminSection';
import { buildRules, formatSideBetRounds, isReadableSideBetRounds } from './rulesInput';

function CreateSeasonSection(): ReactNode {
  const defaultsQuery = useQuery({
    queryKey: queryKeys.defaultSeasonRules.root,
    queryFn: api.defaultSeasonRules,
    // Never refetched: a failed background refetch would swap the form for an error and drop the edits.
    staleTime: Infinity,
  });

  return (
    <AdminSection title="Create Season">
      <QueryState query={defaultsQuery} label="default season rules">
        {(defaults) => <CreateSeasonForm defaults={defaults} />}
      </QueryState>
    </AdminSection>
  );
}

interface CreateSeasonFormProps {
  defaults: SeasonRules;
}

function CreateSeasonForm({ defaults }: CreateSeasonFormProps): ReactNode {
  const queryClient = useQueryClient();

  const { leagues, leagueId, setLeagueId } = useAdminLeague();
  const [seasonYear, setSeasonYear] = useState(String(new Date().getFullYear()));
  const [seasonName, setSeasonName] = useState('');
  // Null until edited, rather than seeded from `defaults`, so no prop is copied into state.
  const [editedPayouts, setEditedPayouts] = useState<string[] | null>(null);
  const [editedTieFloor, setEditedTieFloor] = useState<string | null>(null);
  const [editedSideBetRounds, setEditedSideBetRounds] = useState<string | null>(null);
  const [editedSideBetAmount, setEditedSideBetAmount] = useState<string | null>(null);
  const payouts = editedPayouts ?? defaults.payouts.map(String);
  const tieFloor = editedTieFloor ?? String(defaults.tieFloor);
  const sideBetRoundsStr = editedSideBetRounds ?? formatSideBetRounds(defaults.sideBetRounds);
  const sideBetAmount = editedSideBetAmount ?? String(defaults.sideBetAmount);
  // Numbers are kept as typed: Number('') is 0, so a cleared field would otherwise submit a zero nobody entered.
  const fieldCleared = [seasonYear, ...payouts, tieFloor, sideBetAmount].includes('');
  // The parse drops what it can't read, and an empty list means no side bets, so a misread must not submit.
  const roundsUnreadable = !isReadableSideBetRounds(sideBetRoundsStr);
  const [success, setSuccess] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: () =>
      api.createSeason({
        leagueId,
        name: seasonName.trim(),
        seasonYear: Number(seasonYear),
        rules: buildRules({
          payouts: payouts.map(Number),
          tieFloor: Number(tieFloor),
          sideBetRoundsStr,
          sideBetAmount: Number(sideBetAmount),
        }),
      }),
    onSuccess: (season: Season) => {
      setSuccess(`Season "${season.name}" created`);
      void queryClient.invalidateQueries({ queryKey: queryKeys.seasons.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dataHealth.root });
    },
  });

  const errorMessage = mutationError(createMutation.error);

  const disabled = createMutation.isPending || !seasonName.trim() || !leagueId || fieldCleared || roundsUnreadable;

  const updatePayout = (index: number, value: string) => {
    setEditedPayouts(payouts.map((p, i) => (i === index ? value : p)));
  };

  return (
    <>
      <div className="flex flex-wrap gap-4 mb-4">
        <AdminLeagueField id="season-league" leagues={leagues} leagueId={leagueId} onChange={setLeagueId} />
        <FieldShell id="season-year" label="Year" error={undefined} width="w-24">
          <input
            id="season-year"
            type="number"
            value={seasonYear}
            onChange={(e) => setSeasonYear(e.target.value)}
            className={FIELD_CLASS}
          />
        </FieldShell>
        <TextField
          id="season-name"
          label="Season Name"
          value={seasonName}
          error={undefined}
          placeholder="e.g. Spring"
          width="sm:w-48"
          onChange={setSeasonName}
        />
      </div>

      <div className="mb-4">
        <h4 className="text-xs text-ink-muted font-semibold uppercase tracking-wider mb-2">
          Payout Rules
        </h4>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 mb-3">
          {payouts.map((amt, i) => (
            // eslint-disable-next-line react/no-array-index-key -- a payout row is its finishing position; rows only append or pop.
            <div key={i} className="flex items-center gap-1">
              <span className="text-xs text-ink-soft w-6">{i + 1}.</span>
              <span className="text-xs text-ink-soft">$</span>
              <input
                type="number"
                min="0"
                aria-label={`Payout ${i + 1}`}
                value={amt}
                onChange={(e) => updatePayout(i, e.target.value)}
                className={`bg-surface-raised ${CONTROL_CLASS} ${TAP_TARGET_CLASS} w-16`}
              />
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-4 items-end mb-3">
          <div>
            <button
              type="button"
              onClick={() => setEditedPayouts([...payouts, '0'])}
              className={`text-xs text-ink-muted hover:text-ink-strong ${TAP_TARGET_CLASS}`}
            >
              + Add place
            </button>
            <button
              type="button"
              onClick={() => setEditedPayouts(payouts.length > 1 ? payouts.slice(0, -1) : payouts)}
              className={`text-xs text-semantic-danger hover:text-semantic-danger-hover ml-2 ${TAP_TARGET_CLASS}`}
            >
              - Remove last
            </button>
          </div>
          <FieldShell id="season-tie-floor" label="Tie Floor ($)" error={undefined} width="w-24">
            <input
              id="season-tie-floor"
              type="number"
              min="0"
              step="0.5"
              value={tieFloor}
              onChange={(e) => setEditedTieFloor(e.target.value)}
              className={FIELD_CLASS}
            />
          </FieldShell>
        </div>

        <h4 className="text-xs text-ink-muted font-semibold uppercase tracking-wider mb-2 mt-4">
          Side Bets
        </h4>
        <div className="flex flex-wrap gap-4 items-end">
          <TextField
            id="season-side-rounds"
            label="Rounds"
            value={sideBetRoundsStr}
            error={roundsUnreadable ? 'Round numbers, comma-separated; blank for none' : undefined}
            placeholder="none"
            width="w-28"
            onChange={setEditedSideBetRounds}
          />
          <FieldShell id="season-side-amount" label="Bet Amount ($)" error={undefined} width="w-24">
            <input
              id="season-side-amount"
              type="number"
              min="0"
              value={sideBetAmount}
              onChange={(e) => setEditedSideBetAmount(e.target.value)}
              className={FIELD_CLASS}
            />
          </FieldShell>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <Button tone="primary" size="regular" onClick={() => createMutation.mutate()} disabled={disabled}>
          {createMutation.isPending ? 'Creating...' : 'Create Season'}
        </Button>
        {success ? <span className="text-semantic-success text-sm">{success}</span> : null}
        {errorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>
    </>
  );
}

export default CreateSeasonSection;
