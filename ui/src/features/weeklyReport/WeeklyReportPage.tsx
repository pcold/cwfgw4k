import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import PageTitle from '@/shared/components/PageTitle';
import PickerRow from '@/shared/components/PickerRow';
import StatusChip, { statusChipVariant } from '@/shared/components/StatusChip';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import type { Season, WeeklyReport } from '@/shared/api/types';
import Button from '@/shared/components/Button';
import WeeklyReportView from './WeeklyReportView';
import { reportSubtitle } from './weeklyReportModel';

function downloadPdf(report: WeeklyReport, season: Season | null): Promise<void> {
  return import('./weeklyReportPdf').then((m) => m.downloadWeeklyReportPdf(report, season));
}

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

  function renderBody(): ReactNode {
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
      <>
        <PickerRow
          id="weekly-report-tournament"
          label="Tournament"
          value={effectiveId}
          options={tournamentOptions}
          onChange={setUserTournamentId}
        >
          <Button
            tone="secondary"
            size="regular"
            disabled={!reportQuery.data || pdfMutation.isPending}
            onClick={() => reportQuery.data && pdfMutation.mutate(reportQuery.data)}
          >
            Download PDF
          </Button>
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
      </>
    );
  }

  return (
    <div className="space-y-4">
      <PageTitle
        title="Weekly Report"
        subtitle={reportQuery.data ? reportSubtitle(reportQuery.data) : undefined}
        actions={
          <>
            <StatusChip variant={statusChipVariant(reportQuery.data)} />
            <LeagueSeasonPicker />
          </>
        }
      />
      {renderBody()}
      <GolferHistoryModal
        seasonId={seasonId}
        golferId={historyGolferId}
        onClose={() => setHistoryGolferId(null)}
      />
    </div>
  );
}

export default WeeklyReportPage;
