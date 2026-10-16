import { useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import PickerRow from '@/shared/components/PickerRow';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import LateRowBetsView from './LateRowBetsView';

function LateRowBetsPage(): ReactNode {
  const { seasonId, live } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  const [userTournamentId, setUserTournamentId] = useState<string | null>(null);

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

  const reportQuery = useQuery({
    queryKey: queryKeys.report.detail(seasonId, effectiveId, live),
    queryFn:
      seasonId === null || effectiveId === null
        ? skipToken
        : () =>
            effectiveId === ALL_TOURNAMENTS
              ? api.seasonReport(seasonId)
              : api.tournamentReport(seasonId, effectiveId, live),
  });

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
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-bold">Late Row Bets</h2>
        <PickerRow
          id="late-row-bets-through"
          label="Through"
          value={effectiveId}
          options={tournamentOptions}
          onChange={setUserTournamentId}
        />
      </div>

      <QueryState query={reportQuery} label="report">
        {(report) => <LateRowBetsView report={report} />}
      </QueryState>
    </div>
  );
}

export default LateRowBetsPage;
