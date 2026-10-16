import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import PickerRow from '@/shared/components/PickerRow';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import type { Season, WeeklyReport } from '@/shared/api/types';
import WeeklyReportView from './WeeklyReportView';

function downloadPdf(report: WeeklyReport, season: Season | null): Promise<void> {
  return import('./weeklyReportPdf').then((m) => m.downloadWeeklyReportPdf(report, season));
}

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
  const currentQuery = useQuery({
    queryKey: queryKeys.tournaments.current(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.currentTournament(seasonId),
  });

  const pdfMutation = useMutation({
    mutationFn: (report: WeeklyReport) => downloadPdf(report, currentSeason),
  });

  const tournaments = tournamentsQuery.data;
  const effectiveId =
    tournaments === undefined
      ? null
      : (pickInSeason(userTournamentId, tournaments) ??
        pickInSeason(defaultReportSelection(currentQuery.data), tournaments));
  const { query: reportQuery, overlay: liveOverlay } = useLiveOverlayQuery(
    (live) => queryKeys.report.detail(seasonId, effectiveId, live),
    seasonId === null || effectiveId === null
      ? null
      : (live) =>
          effectiveId === ALL_TOURNAMENTS
            ? api.seasonReport(seasonId)
            : api.tournamentReport(seasonId, effectiveId, live),
  );

  if (leaguesGate) return leaguesGate;
  if (tournaments === undefined || effectiveId === null) {
    return (
      <QueryState query={tournamentsQuery} label="tournaments">
        {() => (
          <QueryState query={currentQuery} label="current tournament">
            {() => null}
          </QueryState>
        )}
      </QueryState>
    );
  }

  const tournamentOptions = [
    { value: ALL_TOURNAMENTS, label: 'All Tournaments' },
    ...tournaments.map((t) => ({ value: t.id, label: tournamentLabel(t) })),
  ];

  return (
    <div className="space-y-4">
      <PickerRow
        id="weekly-report-tournament"
        label="Tournament"
        value={effectiveId}
        options={tournamentOptions}
        onChange={setUserTournamentId}
      >
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
      </PickerRow>

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
