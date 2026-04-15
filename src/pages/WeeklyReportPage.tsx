import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/components/QueryState';
import GolferHistoryModal from '@/components/GolferHistoryModal';
import { tournamentLabel } from '@/util/tournament';
import WeeklyReportView from './WeeklyReportView';

const ALL_TOURNAMENTS = '';

function WeeklyReportPage() {
  const { seasonId, live } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [tournamentId, setTournamentId] = useState<string>(ALL_TOURNAMENTS);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

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

  if (leaguesGate) return leaguesGate;

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

      <QueryState query={reportQuery} label="report">
        {(report) => <WeeklyReportView report={report} onGolferClick={setHistoryGolferId} />}
      </QueryState>
      <GolferHistoryModal
        seasonId={seasonId}
        golferId={historyGolferId}
        onClose={() => setHistoryGolferId(null)}
      />
    </div>
  );
}

export default WeeklyReportPage;
