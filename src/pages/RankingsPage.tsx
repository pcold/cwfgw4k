import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import type { Tournament } from '@/api/types';
import RankingsView from './RankingsView';

const ALL_TOURNAMENTS = '';

function tournamentLabel(t: Tournament): string {
  const suffix = t.status !== 'completed' ? ` (${t.status})` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${suffix}`;
}

function RankingsPage() {
  const { leagues, leaguesLoading, leaguesError, seasonId, live } = useLeagueSeason();
  const [throughTournamentId, setThroughTournamentId] = useState<string>(ALL_TOURNAMENTS);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: () => api.tournaments(seasonId!),
    enabled: !!seasonId,
  });

  const rankingsQuery = useQuery({
    queryKey: ['rankings', seasonId, live, throughTournamentId],
    queryFn: () =>
      api.rankings(seasonId!, live, throughTournamentId || undefined),
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
          htmlFor="rankings-through-tournament"
          className="text-xs text-gray-400 uppercase tracking-wider"
        >
          Through
        </label>
        <select
          id="rankings-through-tournament"
          className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm"
          value={throughTournamentId}
          onChange={(e) => setThroughTournamentId(e.target.value)}
        >
          <option value={ALL_TOURNAMENTS}>All Tournaments</option>
          {tournaments.map((t) => (
            <option key={t.id} value={t.id}>
              {tournamentLabel(t)}
            </option>
          ))}
        </select>
      </div>

      {rankingsQuery.isLoading ? <p className="text-gray-400">Loading standings…</p> : null}
      {rankingsQuery.isError ? (
        <p className="text-red-400">Failed to load standings: {String(rankingsQuery.error)}</p>
      ) : null}
      {rankingsQuery.data ? <RankingsView rankings={rankingsQuery.data} /> : null}
    </div>
  );
}

export default RankingsPage;
