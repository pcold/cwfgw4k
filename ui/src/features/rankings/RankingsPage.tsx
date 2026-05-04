import { useState } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import { LiveOverlayCheckbox, useLiveOverlay } from '@/shared/components/LiveOverlayToggle';
import { earliestUnfinalized, tournamentLabel } from '@/shared/util/tournament';
import RankingsView from './RankingsView';

const ALL_TOURNAMENTS = '';

function RankingsPage() {
  const { seasonId } = useLeagueSeason();
  const leaguesGate = useLeaguesGate();
  // null = user hasn't picked yet, use the computed default; "" = user
  // explicitly picked All Tournaments; "<uuid>" = explicit tournament.
  const [throughOverride, setThroughOverride] = useState<string | null>(null);

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  const tournaments = tournamentsQuery.data;
  const defaultThrough =
    tournaments === undefined ? null : (earliestUnfinalized(tournaments) ?? ALL_TOURNAMENTS);
  const throughTournamentId = throughOverride ?? defaultThrough;

  const liveOverlay = useLiveOverlay(tournaments ?? [], throughTournamentId);

  const rankingsQuery = useQuery({
    queryKey: ['rankings', seasonId, liveOverlay.effectiveLive, throughTournamentId],
    queryFn:
      seasonId === null || throughTournamentId === null
        ? skipToken
        : () =>
            api.rankings(seasonId, liveOverlay.effectiveLive, throughTournamentId || undefined),
  });

  if (leaguesGate) return leaguesGate;

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
          value={throughTournamentId ?? ALL_TOURNAMENTS}
          onChange={(e) => setThroughOverride(e.target.value)}
        >
          <option value={ALL_TOURNAMENTS}>All Tournaments</option>
          {(tournaments ?? []).map((t) => (
            <option key={t.id} value={t.id}>
              {tournamentLabel(t)}
            </option>
          ))}
        </select>
        <LiveOverlayCheckbox state={liveOverlay} id="rankings-live-overlay" />
      </div>

      <QueryState query={rankingsQuery} label="standings">
        {(rankings) => <RankingsView rankings={rankings} />}
      </QueryState>
    </div>
  );
}

export default RankingsPage;
