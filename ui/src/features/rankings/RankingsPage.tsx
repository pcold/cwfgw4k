import { useState } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import { tournamentLabel } from '@/shared/util/tournament';
import RankingsView from './RankingsView';

const ALL_TOURNAMENTS = '';

function RankingsPage() {
  const { seasonId, live } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [throughTournamentId, setThroughTournamentId] = useState<string>(ALL_TOURNAMENTS);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  const rankingsQuery = useQuery({
    queryKey: ['rankings', seasonId, live, throughTournamentId],
    queryFn:
      seasonId === null
        ? skipToken
        : () => api.rankings(seasonId, live, throughTournamentId || undefined),
  });

  if (leaguesGate) return leaguesGate;

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

      <QueryState query={rankingsQuery} label="standings">
        {(rankings) => <RankingsView rankings={rankings} />}
      </QueryState>
    </div>
  );
}

export default RankingsPage;
