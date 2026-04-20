import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/components/QueryState';
import GolferHistoryModal from '@/components/GolferHistoryModal';
import { earliestUnfinalized, tournamentLabel } from '@/util/tournament';
import WeeklyReportView from './WeeklyReportView';
import type { WeeklyReport } from '@/api/types';

function downloadPdf(report: WeeklyReport): Promise<void> {
  return import('./weeklyReportPdf').then((m) => m.downloadWeeklyReportPdf(report));
}

const ALL_TOURNAMENTS = '';

function WeeklyReportPage() {
  const { seasonId, live } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [tournamentId, setTournamentId] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

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
        <button
          type="button"
          disabled={!reportQuery.data}
          onClick={() => reportQuery.data && downloadPdf(reportQuery.data)}
          className="bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Download PDF
        </button>
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
