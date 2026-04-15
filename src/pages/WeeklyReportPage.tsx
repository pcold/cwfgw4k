import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import type { Tournament } from '@/api/types';
import WeeklyReportView from './WeeklyReportView';

const ALL_TOURNAMENTS = '';

function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` ${t.payoutMultiplier}x` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}

function WeeklyReportPage() {
  const { leagues, leaguesLoading, leaguesError, seasonId, live } = useLeagueSeason();
  const [tournamentId, setTournamentId] = useState<string>(ALL_TOURNAMENTS);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: () => api.tournaments(seasonId!),
    enabled: !!seasonId,
  });

  const reportQuery = useQuery({
    queryKey: ['report', seasonId, tournamentId, live],
    queryFn: () =>
      tournamentId === ALL_TOURNAMENTS
        ? api.seasonReport(seasonId!, live)
        : api.tournamentReport(seasonId!, tournamentId, live),
    enabled: !!seasonId,
  });

  if (leaguesLoading) return <p className="text-gray-400">Loading leagues…</p>;
  if (leaguesError)
    return <p className="text-red-400">Failed to load leagues: {String(leaguesError)}</p>;
  if (!leagues || leagues.length === 0)
    return <p className="text-gray-400">No leagues configured.</p>;

  const tournaments = tournamentsQuery.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <label
          htmlFor="weekly-report-tournament"
          className="text-xs text-gray-400 uppercase tracking-wider"
        >
          Tournament
        </label>
        <select
          id="weekly-report-tournament"
          className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm"
          value={tournamentId}
          onChange={(e) => setTournamentId(e.target.value)}
        >
          <option value={ALL_TOURNAMENTS}>All Tournaments</option>
          {tournaments.map((t) => (
            <option key={t.id} value={t.id}>
              {tournamentLabel(t)}
            </option>
          ))}
        </select>
      </div>

      {reportQuery.isLoading ? <p className="text-gray-400">Loading report…</p> : null}
      {reportQuery.isError ? (
        <p className="text-red-400">Failed to load report: {String(reportQuery.error)}</p>
      ) : null}
      {reportQuery.data ? <WeeklyReportView report={reportQuery.data} /> : null}
    </div>
  );
}

export default WeeklyReportPage;
