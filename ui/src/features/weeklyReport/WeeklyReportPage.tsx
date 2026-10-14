import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlay } from '@/shared/components/LiveOverlayToggle';
import { earliestUnfinalized, tournamentLabel } from '@/shared/util/tournament';
import type { Season, WeeklyReport } from '@/shared/api/types';
import WeeklyReportView from './WeeklyReportView';

function downloadPdf(report: WeeklyReport, season: Season | null): Promise<void> {
  return import('./weeklyReportPdf').then((m) => m.downloadWeeklyReportPdf(report, season));
}

const ALL_TOURNAMENTS = '';

const DOWNLOAD_BUTTON_CLASS =
  'bg-surface-card border border-control-border rounded px-3 py-2 text-sm hover:bg-surface-raised ' +
  'disabled:opacity-50 disabled:cursor-not-allowed';

function WeeklyReportPage(): ReactNode {
  const { seasonId, seasons } = useLeagueSeason();
  const currentSeason = seasons?.find((s) => s.id === seasonId) ?? null;
  const leaguesGate = useLeaguesGate();
  const [userTournamentId, setUserTournamentId] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  const pdfMutation = useMutation({
    mutationFn: (report: WeeklyReport) => downloadPdf(report, currentSeason),
  });

  const tournaments = tournamentsQuery.data ?? [];
  // Derived rather than synced: user's pick wins, otherwise the earliest
  // unfinalized tournament, otherwise the "All Tournaments" sentinel.
  const effectiveId = userTournamentId ?? earliestUnfinalized(tournaments) ?? ALL_TOURNAMENTS;
  const liveOverlay = useLiveOverlay(tournaments, effectiveId || null);

  const reportQuery = useQuery({
    queryKey: queryKeys.report.detail(seasonId, effectiveId, liveOverlay.effectiveLive),
    queryFn:
      seasonId === null
        ? skipToken
        : () =>
            effectiveId === ALL_TOURNAMENTS
              ? api.seasonReport(seasonId, liveOverlay.effectiveLive)
              : api.tournamentReport(seasonId, effectiveId, liveOverlay.effectiveLive),
  });

  if (leaguesGate) return leaguesGate;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <label
          htmlFor="weekly-report-tournament"
          className="text-xs text-ink-muted uppercase tracking-wider"
        >
          Tournament
        </label>
        <select
          id="weekly-report-tournament"
          className="bg-surface-card border border-control-border rounded px-3 py-2 text-sm"
          value={effectiveId}
          onChange={(e) => setUserTournamentId(e.target.value)}
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
          disabled={!reportQuery.data || pdfMutation.isPending}
          onClick={() => reportQuery.data && pdfMutation.mutate(reportQuery.data)}
          className={DOWNLOAD_BUTTON_CLASS}
        >
          Download PDF
        </button>
        {pdfMutation.isError ? (
          <span role="alert" className="text-semantic-danger text-sm">
            Failed to generate PDF: {String(pdfMutation.error)}
          </span>
        ) : null}
        <LiveOverlayCheckbox state={liveOverlay} id="weekly-report-live-overlay" />
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
