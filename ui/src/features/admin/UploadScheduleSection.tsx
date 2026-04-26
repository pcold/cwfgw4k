import { useState } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type { League, Season, SeasonImportResult, Tournament } from '@/shared/api/types';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';

function UploadScheduleSection() {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: ['leagues'], queryFn: api.leagues });
  const [userLeagueId, setUserLeagueId] = useState<string>('');
  const leagueId = userLeagueId || (leaguesQuery.data?.[0]?.id ?? '');

  const seasonsQuery = useQuery<Season[]>({
    queryKey: ['seasons', leagueId],
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });

  const [seasonId, setSeasonId] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  // Tournaments returned by the import POST. Stored locally so we can edit
  // each row's multiplier in place and patch them via PUT one at a time.
  const [createdTournaments, setCreatedTournaments] = useState<Tournament[]>([]);
  const [skippedSummary, setSkippedSummary] = useState<SeasonImportResult['skipped']>([]);

  const importMutation = useMutation({
    mutationFn: () => api.importSeasonSchedule({ seasonId, startDate, endDate }),
    onSuccess: (result) => {
      setCreatedTournaments(result.created);
      setSkippedSummary(result.skipped);
      void queryClient.invalidateQueries({ queryKey: ['tournaments'] });
    },
  });

  const errorMessage = mutationError(importMutation.error);

  const disabled =
    importMutation.isPending ||
    !seasonId ||
    !startDate ||
    !endDate ||
    startDate > endDate;

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Import Season Schedule from ESPN
      </h3>
      <p className="text-xs text-gray-400 mb-4">
        Pulls the PGA tour calendar from ESPN for the date range and creates a tournament for
        every event whose start date falls inside it. Week labels (1, 2, 3a, 3b…) are assigned
        chronologically. Re-running on a season skips events already linked. After import, edit
        the per-tournament payout multiplier inline and Save each row to push the change.
      </p>

      <div className="flex flex-wrap gap-4 mb-4">
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="schedule-league">
            League
          </label>
          <select
            id="schedule-league"
            value={leagueId}
            onChange={(e) => {
              setUserLeagueId(e.target.value);
              setSeasonId('');
            }}
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
          <label className="block text-xs text-gray-400 mb-1" htmlFor="schedule-season">
            Season
          </label>
          <select
            id="schedule-season"
            value={seasonId}
            onChange={(e) => setSeasonId(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-64"
          >
            <option value="" disabled>
              Select a season
            </option>
            {(seasonsQuery.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {seasonLabel(s)}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="schedule-start">
            Start date
          </label>
          <input
            id="schedule-start"
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="schedule-end">
            End date
          </label>
          <input
            id="schedule-end"
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm"
          />
        </div>
      </div>

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={() => importMutation.mutate()}
          disabled={disabled}
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {importMutation.isPending ? 'Importing…' : 'Import from ESPN'}
        </button>
        {errorMessage ? (
          <span role="alert" className="text-red-400 text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>

      {createdTournaments.length > 0 || skippedSummary.length > 0 ? (
        <ImportResult
          created={createdTournaments}
          skipped={skippedSummary}
          onRowSaved={(updated) =>
            setCreatedTournaments((prev) =>
              prev.map((t) => (t.id === updated.id ? updated : t)),
            )
          }
        />
      ) : null}
    </div>
  );
}

interface ImportResultProps {
  created: Tournament[];
  skipped: SeasonImportResult['skipped'];
  onRowSaved: (updated: Tournament) => void;
}

function ImportResult({ created, skipped, onRowSaved }: ImportResultProps) {
  return (
    <div className="mt-6 pt-4 border-t border-gray-700">
      <h4 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Confirm Imported Tournaments
      </h4>

      <div className="flex gap-6 mb-4 text-sm">
        <div>
          <span className="text-gray-400">Created:</span>
          <span className="text-green-400 font-bold ml-1">{created.length}</span>
        </div>
        {skipped.length > 0 ? (
          <div>
            <span className="text-gray-400">Skipped:</span>
            <span className="text-yellow-400 font-bold ml-1">{skipped.length}</span>
          </div>
        ) : null}
      </div>

      {skipped.length > 0 ? (
        <div className="mb-4 px-3 py-2 bg-yellow-900/30 border border-yellow-700 rounded text-yellow-300 text-xs">
          <div className="font-semibold mb-1">Skipped ESPN entries:</div>
          <ul className="space-y-0.5">
            {skipped.map((s) => (
              <li key={s.espnEventId}>
                <span className="font-mono">{s.espnEventName}</span> — {s.reason}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {created.length > 0 ? (
        <table className="w-full text-sm">
          <thead className="bg-gray-700 text-gray-300 text-xs uppercase tracking-wider">
            <tr>
              <th className="px-3 py-2 text-left">Wk</th>
              <th className="px-3 py-2 text-left">Tournament</th>
              <th className="px-3 py-2 text-left">Dates</th>
              <th className="px-3 py-2 text-center">Mult</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {created.map((tournament) => (
              <TournamentRow
                key={tournament.id}
                tournament={tournament}
                onSaved={onRowSaved}
              />
            ))}
          </tbody>
        </table>
      ) : null}
    </div>
  );
}

interface TournamentRowProps {
  tournament: Tournament;
  onSaved: (updated: Tournament) => void;
}

function TournamentRow({ tournament, onSaved }: TournamentRowProps) {
  const [draftMultiplier, setDraftMultiplier] = useState<string>(String(tournament.payoutMultiplier));

  const updateMutation = useMutation({
    mutationFn: () => {
      const parsed = Number.parseFloat(draftMultiplier);
      return api.updateTournament(tournament.id, { payoutMultiplier: parsed });
    },
    onSuccess: (updated) => {
      onSaved(updated);
      setDraftMultiplier(String(updated.payoutMultiplier));
    },
  });

  const draftValue = Number.parseFloat(draftMultiplier);
  const dirty =
    draftMultiplier.trim() !== '' &&
    Number.isFinite(draftValue) &&
    draftValue !== tournament.payoutMultiplier;
  const invalid = draftMultiplier.trim() !== '' && (!Number.isFinite(draftValue) || draftValue <= 0);
  const errorMessage = mutationError(updateMutation.error);

  return (
    <tr className="border-t border-gray-700">
      <td className="px-3 py-2 text-gray-400 font-mono">{tournament.week ?? ''}</td>
      <td className="px-3 py-2 font-medium">{tournament.name}</td>
      <td className="px-3 py-2 text-gray-400 text-xs">
        {tournament.startDate} to {tournament.endDate}
      </td>
      <td className="px-3 py-2 text-center">
        <input
          type="number"
          min="0.5"
          step="0.5"
          value={draftMultiplier}
          onChange={(e) => setDraftMultiplier(e.target.value)}
          aria-label={`Payout multiplier for ${tournament.name}`}
          className={`bg-gray-700 border ${invalid ? 'border-red-500' : 'border-gray-600'} rounded px-2 py-1 text-sm w-20 text-right`}
        />
      </td>
      <td className="px-3 py-2 text-xs">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => updateMutation.mutate()}
            disabled={!dirty || invalid || updateMutation.isPending}
            className="bg-green-600 hover:bg-green-700 disabled:bg-gray-700 disabled:text-gray-500 text-white px-3 py-1 rounded"
          >
            {updateMutation.isPending ? 'Saving…' : 'Save'}
          </button>
          {errorMessage ? (
            <span role="alert" className="text-red-400">
              {errorMessage}
            </span>
          ) : null}
        </div>
      </td>
    </tr>
  );
}

export default UploadScheduleSection;
