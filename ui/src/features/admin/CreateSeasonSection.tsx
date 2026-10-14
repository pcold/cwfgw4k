import { useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { League, Season, SeasonRules } from '@/shared/api/types';
import { FIELD_CLASS, FieldShell, TextField } from '@/shared/components/formFields';
import { QueryState } from '@/shared/components/QueryState';
import { mutationError } from '@/shared/util/mutationError';
import { buildRules, formatSideBetRounds } from './rulesInput';

function CreateSeasonSection(): ReactNode {
  const defaultsQuery = useQuery({
    queryKey: queryKeys.defaultSeasonRules.root,
    queryFn: api.defaultSeasonRules,
  });

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Create Season
      </h3>
      <QueryState query={defaultsQuery} label="default season rules">
        {(defaults) => <CreateSeasonForm defaults={defaults} />}
      </QueryState>
    </div>
  );
}

interface CreateSeasonFormProps {
  defaults: SeasonRules;
}

function CreateSeasonForm({ defaults }: CreateSeasonFormProps): ReactNode {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: queryKeys.leagues.root, queryFn: api.leagues });

  const [userLeagueId, setUserLeagueId] = useState<string>('');
  // User's pick wins; otherwise default to the first loaded league.
  const leagueId = userLeagueId || (leaguesQuery.data?.[0]?.id ?? '');
  const [seasonYear, setSeasonYear] = useState<number>(new Date().getFullYear());
  const [seasonName, setSeasonName] = useState('');
  // Null until edited, rather than seeded from `defaults`, so no prop is copied into state.
  const [editedPayouts, setEditedPayouts] = useState<number[] | null>(null);
  const [editedTieFloor, setEditedTieFloor] = useState<number | null>(null);
  const [editedSideBetRounds, setEditedSideBetRounds] = useState<string | null>(null);
  const [editedSideBetAmount, setEditedSideBetAmount] = useState<number | null>(null);
  const payouts = editedPayouts ?? defaults.payouts;
  const tieFloor = editedTieFloor ?? defaults.tieFloor;
  const sideBetRoundsStr = editedSideBetRounds ?? formatSideBetRounds(defaults.sideBetRounds);
  const sideBetAmount = editedSideBetAmount ?? defaults.sideBetAmount;
  const [success, setSuccess] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: () =>
      api.createSeason({
        leagueId,
        name: seasonName.trim(),
        seasonYear,
        rules: buildRules({ payouts, tieFloor, sideBetRoundsStr, sideBetAmount }),
      }),
    onSuccess: (season: Season) => {
      setSuccess(`Season "${season.name}" created`);
      void queryClient.invalidateQueries({ queryKey: queryKeys.seasons.root });
    },
  });

  const errorMessage = mutationError(createMutation.error);

  const disabled = createMutation.isPending || !seasonName.trim() || !leagueId;

  const updatePayout = (index: number, value: number) => {
    setEditedPayouts(payouts.map((p, i) => (i === index ? value : p)));
  };

  return (
    <>
      <div className="flex flex-wrap gap-4 mb-4">
        <FieldShell id="season-league" label="League" error={undefined} width="sm:w-64">
          <select
            id="season-league"
            value={leagueId}
            onChange={(e) => setUserLeagueId(e.target.value)}
            className={FIELD_CLASS}
          >
            {(leaguesQuery.data ?? []).map((lg) => (
              <option key={lg.id} value={lg.id}>
                {lg.name}
              </option>
            ))}
          </select>
        </FieldShell>
        <FieldShell id="season-year" label="Year" error={undefined} width="w-24">
          <input
            id="season-year"
            type="number"
            value={seasonYear}
            onChange={(e) => setSeasonYear(Number(e.target.value))}
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
        <h4 className="text-xs text-gray-400 font-semibold uppercase tracking-wider mb-2">
          Payout Rules
        </h4>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 mb-3">
          {payouts.map((amt, i) => (
            // eslint-disable-next-line react/no-array-index-key -- a payout row is its finishing position; rows only append or pop.
            <div key={i} className="flex items-center gap-1">
              <span className="text-xs text-gray-500 w-6">{i + 1}.</span>
              <span className="text-xs text-gray-500">$</span>
              <input
                type="number"
                aria-label={`Payout ${i + 1}`}
                value={amt}
                onChange={(e) => updatePayout(i, Number(e.target.value))}
                className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm w-16"
              />
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-4 items-end mb-3">
          <div>
            <button
              type="button"
              onClick={() => setEditedPayouts([...payouts, 0])}
              className="text-xs text-blue-400 hover:text-blue-300"
            >
              + Add place
            </button>
            <button
              type="button"
              onClick={() => setEditedPayouts(payouts.length > 1 ? payouts.slice(0, -1) : payouts)}
              className="text-xs text-red-400 hover:text-red-300 ml-2"
            >
              - Remove last
            </button>
          </div>
          <FieldShell id="season-tie-floor" label="Tie Floor ($)" error={undefined} width="w-24">
            <input
              id="season-tie-floor"
              type="number"
              step="0.5"
              value={tieFloor}
              onChange={(e) => setEditedTieFloor(Number(e.target.value))}
              className={FIELD_CLASS}
            />
          </FieldShell>
        </div>

        <h4 className="text-xs text-gray-400 font-semibold uppercase tracking-wider mb-2 mt-4">
          Side Bets
        </h4>
        <div className="flex flex-wrap gap-4 items-end">
          <TextField
            id="season-side-rounds"
            label="Rounds"
            value={sideBetRoundsStr}
            error={undefined}
            width="w-28"
            onChange={setEditedSideBetRounds}
          />
          <FieldShell id="season-side-amount" label="Bet Amount ($)" error={undefined} width="w-24">
            <input
              id="season-side-amount"
              type="number"
              value={sideBetAmount}
              onChange={(e) => setEditedSideBetAmount(Number(e.target.value))}
              className={FIELD_CLASS}
            />
          </FieldShell>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={() => createMutation.mutate()}
          disabled={disabled}
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {createMutation.isPending ? 'Creating...' : 'Create Season'}
        </button>
        {success ? <span className="text-green-400 text-sm">{success}</span> : null}
        {errorMessage ? (
          <span role="alert" className="text-red-400 text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>
    </>
  );
}

export default CreateSeasonSection;
