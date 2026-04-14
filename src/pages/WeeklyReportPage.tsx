import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import WeeklyReportView from './WeeklyReportView';

function WeeklyReportPage() {
  const leaguesQuery = useQuery({ queryKey: ['leagues'], queryFn: api.leagues });
  const [leagueId, setLeagueId] = useState<string | null>(null);

  useEffect(() => {
    if (!leagueId && leaguesQuery.data && leaguesQuery.data.length > 0) {
      setLeagueId(leaguesQuery.data[0].id);
    }
  }, [leaguesQuery.data, leagueId]);

  const seasonsQuery = useQuery({
    queryKey: ['seasons', leagueId],
    queryFn: () => api.seasons(leagueId!),
    enabled: !!leagueId,
  });

  const [seasonId, setSeasonId] = useState<string | null>(null);
  useEffect(() => {
    if (!seasonId && seasonsQuery.data && seasonsQuery.data.length > 0) {
      setSeasonId(seasonsQuery.data[0].id);
    }
  }, [seasonsQuery.data, seasonId]);

  const [live, setLive] = useState(false);

  const reportQuery = useQuery({
    queryKey: ['report', seasonId, live],
    queryFn: () => api.seasonReport(seasonId!, live),
    enabled: !!seasonId,
  });

  if (leaguesQuery.isLoading) return <p className="text-gray-400">Loading leagues…</p>;
  if (leaguesQuery.isError)
    return <p className="text-red-400">Failed to load leagues: {String(leaguesQuery.error)}</p>;
  if (!leaguesQuery.data || leaguesQuery.data.length === 0)
    return <p className="text-gray-400">No leagues configured.</p>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-4 text-xs">
        <label className="flex items-center gap-2">
          <span className="text-gray-500 font-medium uppercase tracking-wider">League</span>
          <select
            value={leagueId ?? ''}
            onChange={(e) => setLeagueId(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-2 py-1"
          >
            {leaguesQuery.data.map((lg) => (
              <option key={lg.id} value={lg.id}>
                {lg.name}
              </option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2">
          <span className="text-gray-500 font-medium uppercase tracking-wider">Season</span>
          <select
            value={seasonId ?? ''}
            onChange={(e) => setSeasonId(e.target.value)}
            disabled={!seasonsQuery.data}
            className="bg-gray-700 border border-gray-600 rounded px-2 py-1 disabled:opacity-50"
          >
            {(seasonsQuery.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={live}
            onChange={(e) => setLive(e.target.checked)}
            className="accent-green-500"
          />
          <span className="text-gray-400">Live overlay</span>
        </label>
      </div>

      {reportQuery.isLoading && <p className="text-gray-400">Loading report…</p>}
      {reportQuery.isError && (
        <p className="text-red-400">Failed to load report: {String(reportQuery.error)}</p>
      )}
      {reportQuery.data && <WeeklyReportView report={reportQuery.data} />}
    </div>
  );
}

export default WeeklyReportPage;
