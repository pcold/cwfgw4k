import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import PageTitle from '@/shared/components/PageTitle';
import PickerRow from '@/shared/components/PickerRow';
import StatusChip, { statusChipVariant } from '@/shared/components/StatusChip';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import PhonePicker, { DESKTOP_PICKER_CLASS } from '@/features/leagues/PhonePicker';
import type { Tournament } from '@/shared/api/types';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import RankingsView from './RankingsView';

function RankingsPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [throughOverride, setThroughOverride] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });
  const currentQuery = useQuery({
    queryKey: queryKeys.tournaments.current(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.currentTournament(seasonId),
  });

  const tournaments = tournamentsQuery.data;
  const throughTournamentId =
    tournaments === undefined
      ? null
      : (pickInSeason(throughOverride, tournaments) ??
        pickInSeason(defaultReportSelection(currentQuery.data), tournaments));

  const { query: rankingsQuery, overlay: liveOverlay } = useLiveOverlayQuery(
    (live) => queryKeys.rankings.detail(seasonId, live, throughTournamentId),
    seasonId === null || throughTournamentId === null
      ? null
      : (live) => api.rankings(seasonId, live, throughTournamentId || undefined),
  );

  const rankings = rankingsQuery.data;

  function renderThroughField(loaded: readonly Tournament[], selectedId: string, idSuffix: string): ReactNode {
    const tournamentOptions = [
      { value: ALL_TOURNAMENTS, label: 'All Tournaments' },
      ...loaded.map((t) => ({ value: t.id, label: tournamentLabel(t) })),
    ];

    return (
      <PickerRow
        id={`rankings-through-tournament${idSuffix}`}
        label="Through"
        value={selectedId}
        options={tournamentOptions}
        onChange={setThroughOverride}
      >
        <LiveOverlayCheckbox state={liveOverlay} id={`rankings-live-overlay${idSuffix}`} />
      </PickerRow>
    );
  }

  function renderBody(): ReactNode {
    if (leaguesGate) return leaguesGate;
    if (tournaments === undefined || throughTournamentId === null) {
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
        <div className={DESKTOP_PICKER_CLASS}>{renderThroughField(tournaments, throughTournamentId, '')}</div>

        <QueryState query={rankingsQuery} label="standings">
          {(loaded) => <RankingsView rankings={loaded} />}
        </QueryState>
      </>
    );
  }

  return (
    <div className="space-y-4">
      <PageTitle
        title="Team Standings"
        subtitle={rankings ? `${rankings.weeks.length} weeks played` : undefined}
        actions={
          <>
            <StatusChip variant={statusChipVariant(rankings)} />
            <div className={DESKTOP_PICKER_CLASS}>
              <LeagueSeasonPicker />
            </div>
            <PhonePicker
              tournament={tournaments?.find((t) => t.id === throughTournamentId)}
              renderField={
                tournaments === undefined || throughTournamentId === null
                  ? undefined
                  : (idSuffix) => renderThroughField(tournaments, throughTournamentId, idSuffix)
              }
            />
          </>
        }
      />
      {renderBody()}
    </div>
  );
}

export default RankingsPage;
