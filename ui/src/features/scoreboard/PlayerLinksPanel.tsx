import { useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreOwnerChange, queryKeys } from '@/shared/api/queryKeys';
import ModalShell from '@/shared/components/ModalShell';
import { QueryState } from '@/shared/components/QueryState';
import { mutationError } from '@/shared/util/mutationError';
import type {
  Golfer,
  TournamentCompetitorListing,
  TournamentCompetitorView,
} from '@/shared/api/types';

const SAVE_LINK_BUTTON_CLASS =
  'bg-semantic-info-fill hover:bg-semantic-info-fill-hover disabled:bg-disabled text-on-fill ' +
  'text-xs px-3 py-1 rounded font-medium';

interface PlayerLinksPanelProps {
  tournamentId: string;
  seasonId: string;
  onClose: () => void;
}

function PlayerLinksPanel({ tournamentId, seasonId, onClose }: PlayerLinksPanelProps): ReactNode {
  const competitorsQuery = useQuery({
    queryKey: queryKeys.tournamentCompetitors.byTournament(tournamentId),
    queryFn: () => api.tournamentCompetitors(tournamentId),
  });
  const golfersQuery = useQuery({
    queryKey: queryKeys.golfers.list(),
    queryFn: () => api.golfers(),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <ModalShell title="Manage player links" onClose={onClose} width="wide">
      <div className="space-y-4">
        <QueryState query={competitorsQuery} label="competitors">
          {(listing) => (
            <QueryState query={golfersQuery} label="golfers">
              {(golfers) => (
                <PlayerLinksBody
                  listing={listing}
                  golfers={golfers}
                  tournamentId={tournamentId}
                  seasonId={seasonId}
                />
              )}
            </QueryState>
          )}
        </QueryState>
      </div>
    </ModalShell>
  );
}

interface PlayerLinksBodyProps {
  listing: TournamentCompetitorListing;
  golfers: readonly Golfer[];
  tournamentId: string;
  seasonId: string;
}

function PlayerLinksBody({
  listing,
  golfers,
  tournamentId,
  seasonId,
}: PlayerLinksBodyProps) {
  if (listing.competitors.length === 0) {
    return (
      <p className="text-ink-muted text-sm">
        No ESPN competitors found for this tournament.
      </p>
    );
  }
  return (
    <>
      {listing.isFinalized ? (
        <p
          role="status"
          className="text-semantic-warning-ink text-xs bg-semantic-warning-tint/30 border border-semantic-warning-soft rounded px-3 py-2"
        >
          Links locked — tournament finalized.
        </p>
      ) : null}
      <ul className="divide-y divide-hairline">
        {listing.competitors.map((competitor) => (
          <CompetitorRow
            key={competitor.espnCompetitorId}
            competitor={competitor}
            golfers={golfers}
            tournamentId={tournamentId}
            seasonId={seasonId}
            disabled={listing.isFinalized}
          />
        ))}
      </ul>
    </>
  );
}

interface CompetitorRowProps {
  competitor: TournamentCompetitorView;
  golfers: readonly Golfer[];
  tournamentId: string;
  seasonId: string;
  disabled: boolean;
}

function CompetitorRow({
  competitor,
  golfers,
  tournamentId,
  seasonId,
  disabled,
}: CompetitorRowProps) {
  const queryClient = useQueryClient();
  const [draftGolferId, setDraftGolferId] = useState<string>(
    competitor.linkedGolfer?.id ?? '',
  );
  const [creating, setCreating] = useState(false);

  // The live overlay resolves ESPN competitors through these links on the server, so every read
  // that can carry the overlay is affected, not only the scoreboard's own report.
  function invalidateAffected() {
    void queryClient.invalidateQueries({ queryKey: queryKeys.tournamentCompetitors.byTournament(tournamentId) });
    invalidateAfterScoreOwnerChange(queryClient, { type: 'season', seasonId });
  }

  const saveMutation = useMutation({
    mutationFn: (golferId: string) =>
      api.upsertTournamentPlayerOverride(tournamentId, {
        espnCompetitorId: competitor.espnCompetitorId,
        golferId,
      }),
    onSuccess: invalidateAffected,
  });

  const clearMutation = useMutation({
    mutationFn: () =>
      api.deleteTournamentPlayerOverride(tournamentId, competitor.espnCompetitorId),
    onSuccess: invalidateAffected,
  });

  // Chained: create the placeholder golfer (no pgaPlayerId so future imports
  // don't auto-match against ESPN ids), then immediately pin this row to the
  // new golfer. The two writes are sequential — if upsert fails the new
  // golfer remains in the DB and the admin can pick them from the dropdown.
  const createMutation = useMutation({
    mutationFn: async (input: { firstName: string; lastName: string }) => {
      const created = await api.createGolfer(input);
      await api.upsertTournamentPlayerOverride(tournamentId, {
        espnCompetitorId: competitor.espnCompetitorId,
        golferId: created.id,
      });
      return created;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.golfers.list() });
      invalidateAffected();
      setCreating(false);
    },
  });

  const errorMessage = mutationError(
    saveMutation.error ?? clearMutation.error ?? createMutation.error,
  );
  const pending = saveMutation.isPending || clearMutation.isPending || createMutation.isPending;
  const hasChange = draftGolferId !== '' && draftGolferId !== competitor.linkedGolfer?.id;

  const sortedGolfers = useMemo(
    () =>
      [...golfers].sort((a, b) => {
        const last = a.lastName.localeCompare(b.lastName);
        return last !== 0 ? last : a.firstName.localeCompare(b.firstName);
      }),
    [golfers],
  );

  const selectId = `golfer-select-${competitor.espnCompetitorId}`;

  return (
    <li className="py-3 grid gap-2 sm:grid-cols-[1fr_auto] sm:items-center">
      <div className="space-y-1">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-ink-strong text-sm font-medium">{competitor.name}</span>
          <span className="text-xs text-ink-muted">pos {competitor.position}</span>
          {competitor.isTeamPartner ? (
            <span className="text-xs uppercase tracking-wider bg-surface-raised text-ink-body rounded px-1.5 py-0.5">
              partner
            </span>
          ) : null}
          {competitor.hasOverride ? (
            <span className="text-xs uppercase tracking-wider bg-semantic-info-soft text-semantic-info-ink rounded px-1.5 py-0.5">
              manual override
            </span>
          ) : null}
        </div>
        <label htmlFor={selectId} className="block text-xs text-ink-muted">
          Link to golfer
        </label>
        <select
          id={selectId}
          value={draftGolferId}
          disabled={disabled || pending}
          onChange={(e) => setDraftGolferId(e.target.value)}
          className="bg-surface-raised border border-control-border rounded px-2 py-1 text-sm w-full max-w-sm disabled:opacity-50"
        >
          <option value="">— unlinked —</option>
          {sortedGolfers.map((golfer) => (
            <option key={golfer.id} value={golfer.id}>
              {golfer.lastName}, {golfer.firstName}
            </option>
          ))}
        </select>
        {errorMessage ? (
          <p role="alert" className="text-semantic-danger-strong text-xs">
            {errorMessage}
          </p>
        ) : null}
        {!disabled ? (
          creating ? (
            <NewGolferInlineForm
              competitorName={competitor.name}
              pending={createMutation.isPending}
              onSubmit={(input) => createMutation.mutate(input)}
              onCancel={() => setCreating(false)}
            />
          ) : (
            <button
              type="button"
              disabled={pending}
              onClick={() => setCreating(true)}
              className="text-semantic-info hover:text-semantic-info-hover text-xs underline disabled:opacity-50"
            >
              + Create new golfer & link
            </button>
          )
        ) : null}
      </div>
      <div className="flex flex-col gap-1 sm:items-end">
        <button
          type="button"
          disabled={disabled || pending || !hasChange}
          onClick={() => saveMutation.mutate(draftGolferId)}
          className={SAVE_LINK_BUTTON_CLASS}
        >
          {saveMutation.isPending ? 'Saving…' : 'Save link'}
        </button>
        {competitor.hasOverride ? (
          <button
            type="button"
            disabled={disabled || pending}
            onClick={() => clearMutation.mutate()}
            className="text-ink-muted hover:text-semantic-danger-hover text-xs disabled:opacity-50"
          >
            {clearMutation.isPending ? 'Clearing…' : 'Clear override'}
          </button>
        ) : null}
      </div>
    </li>
  );
}

interface NewGolferInlineFormProps {
  competitorName: string;
  pending: boolean;
  onSubmit: (input: { firstName: string; lastName: string }) => void;
  onCancel: () => void;
}

function NewGolferInlineForm({
  competitorName,
  pending,
  onSubmit,
  onCancel,
}: NewGolferInlineFormProps) {
  const initial = splitCompetitorName(competitorName);
  const [firstName, setFirstName] = useState(initial.firstName);
  const [lastName, setLastName] = useState(initial.lastName);
  const valid = firstName.trim().length > 0 && lastName.trim().length > 0;

  return (
    <div className="border border-hairline rounded p-2 space-y-2 bg-surface-raised/50">
      <p className="text-xs text-ink-muted">
        Creates a new golfer with no ESPN id and pins this competitor to them.
      </p>
      <div className="flex gap-2 flex-wrap">
        <label className="text-xs text-ink-muted flex flex-col">
          First name
          <input
            type="text"
            value={firstName}
            disabled={pending}
            onChange={(e) => setFirstName(e.target.value)}
            className="bg-surface-raised border border-control-border rounded px-2 py-1 text-sm text-ink-strong disabled:opacity-50"
          />
        </label>
        <label className="text-xs text-ink-muted flex flex-col">
          Last name
          <input
            type="text"
            value={lastName}
            disabled={pending}
            onChange={(e) => setLastName(e.target.value)}
            className="bg-surface-raised border border-control-border rounded px-2 py-1 text-sm text-ink-strong disabled:opacity-50"
          />
        </label>
      </div>
      <div className="flex gap-2 items-center">
        <button
          type="button"
          disabled={!valid || pending}
          onClick={() => onSubmit({ firstName: firstName.trim(), lastName: lastName.trim() })}
          className="bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary text-xs px-3 py-1 rounded font-medium"
        >
          {pending ? 'Creating…' : 'Create & link'}
        </button>
        <button
          type="button"
          disabled={pending}
          onClick={onCancel}
          className="text-ink-muted hover:text-ink-strong text-xs disabled:opacity-50"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

// ESPN partner rows arrive last-name-only ("Fitzpatrick"); regular rows are
// "First Last" ("Alex Fitzpatrick"). Split on the LAST whitespace so compound
// first names ("Min Woo Lee" → "Min Woo" / "Lee") behave sensibly. The admin
// can edit either field afterward.
function splitCompetitorName(name: string): { firstName: string; lastName: string } {
  const trimmed = name.trim();
  if (trimmed.length === 0) return { firstName: '', lastName: '' };
  const lastSpace = trimmed.lastIndexOf(' ');
  if (lastSpace < 0) return { firstName: '', lastName: trimmed };
  return {
    firstName: trimmed.slice(0, lastSpace).trim(),
    lastName: trimmed.slice(lastSpace + 1).trim(),
  };
}

export default PlayerLinksPanel;
