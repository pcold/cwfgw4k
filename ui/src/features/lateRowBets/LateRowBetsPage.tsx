import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import PageTitle from '@/shared/components/PageTitle';
import PickerRow from '@/shared/components/PickerRow';
import StatusChip, { statusChipVariant } from '@/shared/components/StatusChip';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import PhonePicker, { DESKTOP_PICKER_CLASS } from '@/features/leagues/PhonePicker';
import type { Tournament } from '@/shared/api/types';
import QueryState from '@/shared/components/QueryState';
import { useLeaguesGate } from '@/shared/components/useLeaguesGate';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import LateRowBetsView, { type LateRowTab } from './LateRowBetsView';

function LateRowBetsPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [userTournamentId, setUserTournamentId] = useState<string | null>(null);
  const [selectedTab, setSelectedTab] = useState<LateRowTab | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });
  const currentQuery = useQuery({
    queryKey: queryKeys.tournaments.current(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.currentTournament(seasonId),
  });

  const tournaments = tournamentsQuery.data;
  const effectiveId =
    tournaments === undefined
      ? null
      : (pickInSeason(userTournamentId, tournaments) ??
        pickInSeason(defaultReportSelection(currentQuery.data), tournaments));

  const { query: reportQuery, overlay: liveOverlay, selectionKey } = useLiveOverlayQuery(
    (live) => queryKeys.report.detail(seasonId, effectiveId, live),
    seasonId === null || effectiveId === null
      ? null
      : (live) =>
          effectiveId === ALL_TOURNAMENTS
            ? api.seasonReport(seasonId)
            : api.tournamentReport(seasonId, effectiveId, live),
  );

  function renderThroughField(loaded: readonly Tournament[], selectedId: string, idSuffix: string): ReactNode {
    const tournamentOptions = [
      { value: ALL_TOURNAMENTS, label: 'All Tournaments' },
      ...loaded.map((t) => ({ value: t.id, label: tournamentLabel(t) })),
    ];

    return (
      <PickerRow
        id={`late-row-bets-through${idSuffix}`}
        label="Through"
        value={selectedId}
        options={tournamentOptions}
        onChange={setUserTournamentId}
      >
        <LiveOverlayCheckbox state={liveOverlay} id={`late-row-bets-live-overlay${idSuffix}`} />
      </PickerRow>
    );
  }

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

    return (
      <>
        <div className={DESKTOP_PICKER_CLASS}>{renderThroughField(tournaments, effectiveId, '')}</div>

        <QueryState query={reportQuery} label="report">
          {(report) => (
            <LateRowBetsView
              report={report}
              selectionKey={selectionKey}
              selectedTab={selectedTab}
              onSelectTab={setSelectedTab}
            />
          )}
        </QueryState>
      </>
    );
  }

  return (
    <div className="space-y-4">
      <PageTitle
        title="Late Row Bets"
        actions={
          <>
            <StatusChip variant={statusChipVariant(reportQuery.data)} />
            <div className={DESKTOP_PICKER_CLASS}>
              <LeagueSeasonPicker />
            </div>
            <PhonePicker
              tournament={tournaments?.find((t) => t.id === effectiveId)}
              renderField={
                tournaments === undefined || effectiveId === null
                  ? undefined
                  : (idSuffix) => renderThroughField(tournaments, effectiveId, idSuffix)
              }
            />
          </>
        }
      />
      {renderBody()}
    </div>
  );
}

export default LateRowBetsPage;
