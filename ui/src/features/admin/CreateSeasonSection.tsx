import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type { League, Season } from '@/shared/api/types';
import { mutationError } from '@/shared/util/mutationError';
import {
  DEFAULT_PAYOUTS,
  DEFAULT_SIDE_BET_AMOUNT,
  DEFAULT_SIDE_BET_ROUNDS_STR,
  DEFAULT_TIE_FLOOR,
  buildRules,
} from './rulesInput';

function CreateSeasonSection() {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: ['leagues'], queryFn: api.leagues });

  const [userLeagueId, setUserLeagueId] = useState<string>('');
  // User's pick wins; otherwise default to the first loaded league.
  const leagueId = userLeagueId || (leaguesQuery.data?.[0]?.id ?? '');
  const [seasonYear, setSeasonYear] = useState<number>(new Date().getFullYear());
  const [seasonName, setSeasonName] = useState('');
  const [payouts, setPayouts] = useState<number[]>(DEFAULT_PAYOUTS);
  const [tieFloor, setTieFloor] = useState<number>(DEFAULT_TIE_FLOOR);
  const [sideBetRoundsStr, setSideBetRoundsStr] = useState<string>(DEFAULT_SIDE_BET_ROUNDS_STR);
  const [sideBetAmount, setSideBetAmount] = useState<number>(DEFAULT_SIDE_BET_AMOUNT);
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
      void queryClient.invalidateQueries({ queryKey: ['seasons'] });
    },
  });

  const errorMessage = mutationError(createMutation.error);

  const disabled = createMutation.isPending || !seasonName.trim() || !leagueId;

  const updatePayout = (index: number, value: number) => {
    setPayouts((prev) => prev.map((p, i) => (i === index ? value : p)));
  };

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Create Season
      </h3>

      <div className="flex flex-wrap gap-4 mb-4">
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="season-league">
            League
          </label>
          <select
            id="season-league"
            value={leagueId}
            onChange={(e) => setUserLeagueId(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-64"
          >
            {(leaguesQuery.data ?? []).map((lg) => (
              <option key={lg.id} value={lg.id}>
                {lg.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="season-year">
            Year
          </label>
          <input
            id="season-year"
            type="number"
            value={seasonYear}
            onChange={(e) => setSeasonYear(Number(e.target.value))}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-24"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="season-name">
            Season Name
          </label>
          <input
            id="season-name"
            type="text"
            value={seasonName}
            onChange={(e) => setSeasonName(e.target.value)}
            placeholder="e.g. Spring"
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-48"
          />
        </div>
      </div>

      <div className="mb-4">
        <h4 className="text-xs text-gray-400 font-semibold uppercase tracking-wider mb-2">
          Payout Rules
        </h4>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 mb-3">
          {payouts.map((amt, i) => (
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
              onClick={() => setPayouts((prev) => [...prev, 0])}
              className="text-xs text-blue-400 hover:text-blue-300"
            >
              + Add place
            </button>
            <button
              type="button"
              onClick={() =>
                setPayouts((prev) => (prev.length > 1 ? prev.slice(0, -1) : prev))
              }
              className="text-xs text-red-400 hover:text-red-300 ml-2"
            >
              - Remove last
            </button>
          </div>
          <div>
            <label className="block text-xs text-gray-400 mb-1" htmlFor="season-tie-floor">
              Tie Floor ($)
            </label>
            <input
              id="season-tie-floor"
              type="number"
              step="0.5"
              value={tieFloor}
              onChange={(e) => setTieFloor(Number(e.target.value))}
              className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm w-16"
            />
          </div>
        </div>

        <h4 className="text-xs text-gray-400 font-semibold uppercase tracking-wider mb-2 mt-4">
          Side Bets
        </h4>
        <div className="flex flex-wrap gap-4 items-end">
          <div>
            <label className="block text-xs text-gray-400 mb-1" htmlFor="season-side-rounds">
              Rounds
            </label>
            <input
              id="season-side-rounds"
              type="text"
              value={sideBetRoundsStr}
              onChange={(e) => setSideBetRoundsStr(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm w-28"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-400 mb-1" htmlFor="season-side-amount">
              Bet Amount ($)
            </label>
            <input
              id="season-side-amount"
              type="number"
              value={sideBetAmount}
              onChange={(e) => setSideBetAmount(Number(e.target.value))}
              className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm w-16"
            />
          </div>
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
    </div>
  );
}

export default CreateSeasonSection;
