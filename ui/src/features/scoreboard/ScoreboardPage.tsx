import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreChange, queryKeys } from '@/shared/api/queryKeys';
import { useAuth } from '@/features/auth/AuthContext';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState, useLeaguesGate } from '@/shared/components/QueryState';
import GolferHistoryModal from '@/shared/components/GolferHistoryModal';
import { LiveOverlayCheckbox, useLiveOverlayQuery } from '@/shared/components/LiveOverlayToggle';
import { mutationError } from '@/shared/util/mutationError';
import {
  defaultScoreboardSelection,
  pickInSeason,
  tournamentLabel,
} from '@/shared/util/tournament';
import type { WeeklyReport } from '@/shared/api/types';
import FinalizeWarnings from './FinalizeWarnings';
import PlayerLinksPanel from './PlayerLinksPanel';
import ScoreboardView from './ScoreboardView';

const FINALIZE_BUTTON_CLASS =
  'bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary px-3 py-1 rounded text-xs ' +
  'font-medium whitespace-nowrap';

const LINKS_BUTTON_CLASS =
  'bg-semantic-info-fill hover:bg-semantic-info-fill-hover text-on-fill px-3 py-1 rounded text-xs ' +
  'font-medium whitespace-nowrap';

function ScoreboardPage(): ReactNode {
  const { seasonId } = useLeagueSeason();
  const { authenticated, isAdmin } = useAuth();
  const leaguesGate = useLeaguesGate();
  const queryClient = useQueryClient();
  const [userTournamentId, setUserTournamentId] = useState<string | null>(null);
  const [historyGolferId, setHistoryGolferId] = useState<string | null>(null);
  const [linksPanelOpen, setLinksPanelOpen] = useState(false);

  const tournamentsQuery = useQuery({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });
  const currentQuery = useQuery({
    queryKey: queryKeys.tournaments.current(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.currentTournament(seasonId),
  });

  const tournaments = tournamentsQuery.data ?? [];

  const tournamentId =
    pickInSeason(userTournamentId, tournaments) ??
    pickInSeason(defaultScoreboardSelection(currentQuery.data), tournaments);

  const { query: reportQuery, overlay: liveOverlay } = useLiveOverlayQuery(
    (live) => queryKeys.tournamentReport.detail(seasonId, tournamentId, live),
    seasonId === null || tournamentId === null
      ? null
      : (live) => api.tournamentReport(seasonId, tournamentId, live),
  );

  const finalizeMutation = useMutation({
    mutationFn: (id: string) => api.finalizeTournament(id),
    // Pinned because finalizing this week's tournament moves the default selection to the next one, which would
    // hide the warnings below.
    onSuccess: (_finalized, id) => {
      setUserTournamentId(id);
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournaments.bySeason(seasonId) });
      invalidateAfterScoreChange(queryClient, { type: 'season', seasonId });
      // The import can create a golfer or backfill an ESPN id, and either changes what data health finds.
      void queryClient.invalidateQueries({ queryKey: queryKeys.dataHealth.root });
    },
  });
  const finalizeErr = mutationError(finalizeMutation.error);
  // Shown only beside the tournament the admin finalized, so switching tournaments leaves its warnings behind.
  const finalized = finalizeMutation.variables === tournamentId ? finalizeMutation.data : undefined;

  function buildFinalizeSlot(report: WeeklyReport): ReactNode {
    if (!authenticated || !tournamentId) return null;
    const showLinks = isAdmin;
    const showFinalize = isAdmin && report.tournament.status !== 'completed';
    if (!showLinks && !showFinalize) return null;
    return (
      <div className="flex items-center gap-3">
        {finalizeErr ? (
          <span role="alert" className="text-semantic-danger-strong text-xs">
            {finalizeErr}
          </span>
        ) : null}
        {showLinks ? (
          <button
            type="button"
            onClick={() => setLinksPanelOpen(true)}
            className={LINKS_BUTTON_CLASS}
          >
            Manage player links
          </button>
        ) : null}
        {showFinalize ? (
          <button
            type="button"
            onClick={() => finalizeMutation.mutate(tournamentId)}
            disabled={finalizeMutation.isPending}
            className={FINALIZE_BUTTON_CLASS}
          >
            {finalizeMutation.isPending ? 'Finalizing…' : 'Finalize Results'}
          </button>
        ) : null}
      </div>
    );
  }

  if (leaguesGate) return leaguesGate;

  return (
    <QueryState query={tournamentsQuery} label="tournaments">
      {(loadedTournaments) => {
        if (loadedTournaments.length === 0) {
          return <p className="text-ink-muted">No tournaments scheduled for this season.</p>;
        }
        if (tournamentId === null) {
          return (
            <QueryState query={currentQuery} label="current tournament">
              {() => null}
            </QueryState>
          );
        }
        return (
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <label
                htmlFor="tournament-select"
                className="text-xs text-ink-muted uppercase tracking-wider"
              >
                Tournament
              </label>
              <select
                id="tournament-select"
                className="bg-surface-card border border-control-border rounded px-3 py-2 text-sm"
                value={tournamentId}
                onChange={(e) => setUserTournamentId(e.target.value)}
              >
                {loadedTournaments.map((t) => (
                  <option key={t.id} value={t.id}>
                    {tournamentLabel(t)}
                  </option>
                ))}
              </select>
              <LiveOverlayCheckbox state={liveOverlay} id="scoreboard-live-overlay" />
            </div>

            {finalized ? <FinalizeWarnings finalized={finalized} /> : null}
            <QueryState query={reportQuery} label="scoreboard">
              {(report) => (
                <ScoreboardView
                  report={report}
                  finalizeSlot={buildFinalizeSlot(report)}
                  onGolferClick={setHistoryGolferId}
                />
              )}
            </QueryState>
            <GolferHistoryModal
              seasonId={seasonId}
              golferId={historyGolferId}
              onClose={() => setHistoryGolferId(null)}
            />
            {linksPanelOpen && isAdmin && tournamentId && seasonId ? (
              <PlayerLinksPanel
                tournamentId={tournamentId}
                seasonId={seasonId}
                onClose={() => setLinksPanelOpen(false)}
              />
            ) : null}
          </div>
        );
      }}
    </QueryState>
  );
}

export default ScoreboardPage;
