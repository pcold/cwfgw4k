import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/components/QueryState';
import { earliestUnfinalized, tournamentLabel } from '@/util/tournament';
import LateRowBetsView from './LateRowBetsView';

const ALL_TOURNAMENTS = '';

function LateRowBetsPage() {
  const { seasonId, live } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [tournamentId, setTournamentId] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: () => api.tournaments(seasonId!),
    enabled: !!seasonId,
  });

  const tournaments = tournamentsQuery.data ?? [];
  useEffect(() => {
    if (tournamentId === null && tournaments.length > 0) {
      setTournamentId(earliestUnfinalized(tournaments) ?? ALL_TOURNAMENTS);
    }
  }, [tournaments, tournamentId]);

  const effectiveId = tournamentId ?? ALL_TOURNAMENTS;

  const reportQuery = useQuery({
    queryKey: ['report', seasonId, effectiveId, live],
    queryFn: () =>
      effectiveId === ALL_TOURNAMENTS
        ? api.seasonReport(seasonId!, live)
        : api.tournamentReport(seasonId!, effectiveId, live),
    enabled: !!seasonId,
  });

  if (leaguesGate) return leaguesGate;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-bold">Round 5-8 Late Row Bets</h2>
        <div className="flex items-center gap-3">
          <label
            htmlFor="late-row-bets-through"
            className="text-xs text-gray-400 uppercase tracking-wider"
          >
            Through
          </label>
          <select
            id="late-row-bets-through"
            className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm"
            value={effectiveId}
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
      </div>

      <QueryState query={reportQuery} label="report">
        {(report) => <LateRowBetsView report={report} />}
      </QueryState>
    </div>
  );
}

export default LateRowBetsPage;
