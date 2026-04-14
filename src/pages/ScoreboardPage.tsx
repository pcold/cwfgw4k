import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useLeagueSeason } from '../context/LeagueSeasonContext';
import type { Tournament } from '../api/types';
import ScoreboardView from './ScoreboardView';

function pickDefaultTournament(tournaments: Tournament[]): string | null {
  if (tournaments.length === 0) return null;
  const active = tournaments.find((t) => t.status !== 'completed');
  if (active) return active.id;
  return tournaments[0].id;
}

function tournamentLabel(t: Tournament): string {
  const multiplier = t.payoutMultiplier !== 1 ? ` (${t.payoutMultiplier}x)` : '';
  return `Wk ${t.week ?? '?'} — ${t.name}${multiplier} — ${t.status}`;
}

function ScoreboardPage() {
  const { leagues, leaguesLoading, leaguesError, seasonId, live } = useLeagueSeason();
  const [tournamentId, setTournamentId] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: () => api.tournaments(seasonId!),
    enabled: !!seasonId,
  });

  const tournaments = tournamentsQuery.data ?? [];

  useEffect(() => {
    if (tournaments.length === 0) {
      if (tournamentId !== null) setTournamentId(null);
      return;
    }
    if (!tournamentId || !tournaments.some((t) => t.id === tournamentId)) {
      setTournamentId(pickDefaultTournament(tournaments));
    }
  }, [tournaments, tournamentId]);

  const reportQuery = useQuery({
    queryKey: ['tournamentReport', seasonId, tournamentId, live],
    queryFn: () => api.tournamentReport(seasonId!, tournamentId!, live),
    enabled: !!seasonId && !!tournamentId,
  });

  if (leaguesLoading) return <p className="text-gray-400">Loading leagues…</p>;
  if (leaguesError)
    return <p className="text-red-400">Failed to load leagues: {String(leaguesError)}</p>;
  if (!leagues || leagues.length === 0)
    return <p className="text-gray-400">No leagues configured.</p>;

  if (tournamentsQuery.isLoading) return <p className="text-gray-400">Loading tournaments…</p>;
  if (tournamentsQuery.isError)
    return (
      <p className="text-red-400">
        Failed to load tournaments: {String(tournamentsQuery.error)}
      </p>
    );
  if (tournaments.length === 0)
    return <p className="text-gray-400">No tournaments scheduled for this season.</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <label htmlFor="tournament-select" className="text-xs text-gray-400 uppercase tracking-wider">
          Tournament
        </label>
        <select
          id="tournament-select"
          className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm"
          value={tournamentId ?? ''}
          onChange={(e) => setTournamentId(e.target.value)}
        >
          {tournaments.map((t) => (
            <option key={t.id} value={t.id}>
              {tournamentLabel(t)}
            </option>
          ))}
        </select>
      </div>

      {reportQuery.isLoading ? <p className="text-gray-400">Loading scoreboard…</p> : null}
      {reportQuery.isError ? (
        <p className="text-red-400">Failed to load scoreboard: {String(reportQuery.error)}</p>
      ) : null}
      {reportQuery.data ? <ScoreboardView report={reportQuery.data} /> : null}
    </div>
  );
}

export default ScoreboardPage;
