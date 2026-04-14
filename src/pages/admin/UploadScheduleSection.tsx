import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../api/client';
import type { League, ScheduleUploadResult, Season } from '../../api/types';

function seasonLabel(s: Season): string {
  return `${s.seasonYear} ${s.name}`;
}

function UploadScheduleSection() {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: ['leagues'], queryFn: api.leagues });
  const [leagueId, setLeagueId] = useState<string>('');

  useEffect(() => {
    if (!leagueId && leaguesQuery.data && leaguesQuery.data.length > 0) {
      setLeagueId(leaguesQuery.data[0].id);
    }
  }, [leaguesQuery.data, leagueId]);

  const seasonsQuery = useQuery<Season[]>({
    queryKey: ['seasons', leagueId],
    queryFn: () => api.seasons(leagueId),
    enabled: !!leagueId,
  });

  const [seasonId, setSeasonId] = useState<string>('');
  const [scheduleText, setScheduleText] = useState<string>('');

  const uploadMutation = useMutation({
    mutationFn: () => {
      const season = seasonsQuery.data?.find((s) => s.id === seasonId);
      return api.uploadSchedule({
        seasonId,
        seasonYear: season?.seasonYear ?? new Date().getFullYear(),
        schedule: scheduleText,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tournaments'] });
    },
  });

  const result: ScheduleUploadResult | undefined = uploadMutation.data;
  const errorMessage =
    uploadMutation.error instanceof ApiError
      ? uploadMutation.error.message
      : uploadMutation.error instanceof Error
        ? uploadMutation.error.message
        : null;

  const disabled = uploadMutation.isPending || !scheduleText.trim() || !seasonId;

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Upload Season Schedule
      </h3>

      <div className="flex flex-wrap gap-4 mb-4">
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="schedule-league">
            League
          </label>
          <select
            id="schedule-league"
            value={leagueId}
            onChange={(e) => {
              setLeagueId(e.target.value);
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
      </div>

      <div className="mb-4">
        <label className="block text-xs text-gray-400 mb-1" htmlFor="schedule-text">
          Schedule (one tournament per line, optional Nx multiplier)
        </label>
        <textarea
          id="schedule-text"
          value={scheduleText}
          onChange={(e) => setScheduleText(e.target.value)}
          rows={16}
          className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full font-mono leading-relaxed"
          placeholder={`1 1 Jan 15-18 Sony Open\n9 10 March 12-15 The Players Championship 2x\n15 15 April 9-12 The Masters 2x`}
        />
      </div>

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={() => uploadMutation.mutate()}
          disabled={disabled}
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {uploadMutation.isPending ? 'Uploading...' : 'Upload & Validate with ESPN'}
        </button>
        {errorMessage ? (
          <span role="alert" className="text-red-400 text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>

      {result ? <ScheduleResult result={result} /> : null}
    </div>
  );
}

function ScheduleResult({ result }: { result: ScheduleUploadResult }) {
  return (
    <div className="mt-6 pt-4 border-t border-gray-700">
      <h4 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Season Created
      </h4>

      <div className="flex gap-6 mb-4 text-sm">
        <div>
          <span className="text-gray-400">Season:</span>
          <span className="text-white font-bold ml-1">{result.seasonYear}</span>
        </div>
        <div>
          <span className="text-gray-400">Created:</span>
          <span className="text-green-400 font-bold ml-1">{result.tournamentsCreated}</span>
        </div>
        <div>
          <span className="text-gray-400">ESPN Matched:</span>
          <span className="text-green-400 font-bold ml-1">{result.espnMatched}</span>
        </div>
        {result.espnUnmatched.length > 0 ? (
          <div>
            <span className="text-gray-400">Unmatched:</span>
            <span className="text-yellow-400 font-bold ml-1">{result.espnUnmatched.length}</span>
          </div>
        ) : null}
      </div>

      {result.espnUnmatched.length > 0 ? (
        <div className="mb-4 px-3 py-2 bg-yellow-900/30 border border-yellow-700 rounded text-yellow-300 text-xs">
          <span className="font-semibold">ESPN unmatched:</span>
          {result.espnUnmatched.map((name) => (
            <span key={name} className="ml-2">
              {name}
            </span>
          ))}
        </div>
      ) : null}

      <table className="w-full text-sm">
        <thead className="bg-gray-700 text-gray-300 text-xs uppercase tracking-wider">
          <tr>
            <th className="px-3 py-2 text-left">Wk</th>
            <th className="px-3 py-2 text-left">Tournament</th>
            <th className="px-3 py-2 text-left">Dates</th>
            <th className="px-3 py-2 text-center">Mult</th>
            <th className="px-3 py-2 text-left">ESPN Match</th>
          </tr>
        </thead>
        <tbody>
          {result.tournaments.map((t) => (
            <tr key={t.id} className="border-t border-gray-700">
              <td className="px-3 py-2 text-gray-400 font-mono">{t.week ?? ''}</td>
              <td className="px-3 py-2 font-medium">{t.name}</td>
              <td className="px-3 py-2 text-gray-400 text-xs">
                {t.startDate} to {t.endDate}
              </td>
              <td className="px-3 py-2 text-center">
                {t.payoutMultiplier !== 1 ? `${t.payoutMultiplier}x` : ''}
              </td>
              <td
                className={`px-3 py-2 text-xs ${
                  t.espnId ? 'text-green-400' : 'text-yellow-400'
                }`}
              >
                {t.espnId ? `${t.espnName ?? ''} (${t.espnId})` : 'No match'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default UploadScheduleSection;
