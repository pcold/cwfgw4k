import { useState } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type { League, Season, SeasonImportResult } from '@/shared/api/types';
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

  const importMutation = useMutation({
    mutationFn: () => api.importSeasonSchedule({ seasonId, startDate, endDate }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tournaments'] });
    },
  });

  const result: SeasonImportResult | undefined = importMutation.data;
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
        chronologically. Re-running on a season skips events already linked.
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

      {result ? <ImportResult result={result} /> : null}
    </div>
  );
}

function ImportResult({ result }: { result: SeasonImportResult }) {
  const created = result.created;
  const skipped = result.skipped;

  return (
    <div className="mt-6 pt-4 border-t border-gray-700">
      <h4 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Import Result
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
              <th className="px-3 py-2 text-left">ESPN id</th>
            </tr>
          </thead>
          <tbody>
            {created.map((t) => (
              <tr key={t.id} className="border-t border-gray-700">
                <td className="px-3 py-2 text-gray-400 font-mono">{t.week ?? ''}</td>
                <td className="px-3 py-2 font-medium">{t.name}</td>
                <td className="px-3 py-2 text-gray-400 text-xs">
                  {t.startDate} to {t.endDate}
                </td>
                <td className="px-3 py-2 text-center">
                  {t.payoutMultiplier !== 1 ? `${t.payoutMultiplier}x` : ''}
                </td>
                <td className="px-3 py-2 text-xs text-gray-500">{t.pgaTournamentId ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </div>
  );
}

export default UploadScheduleSection;
