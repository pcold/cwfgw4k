import { useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreOwnerChange, queryKeys } from '@/shared/api/queryKeys';
import { FIELD_CLASS, FieldShell, TextField } from '@/shared/components/formFields';
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
  // Null until picked, so a link saved from elsewhere still shows rather than a stale copy of the prop.
  const [pickedGolferId, setPickedGolferId] = useState<string | null>(null);
  const draftGolferId = pickedGolferId ?? competitor.linkedGolfer?.id ?? '';
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

  const createMutation = useMutation({
    mutationFn: (name: string) =>
      api.createGolferAndLink(tournamentId, { espnCompetitorId: competitor.espnCompetitorId, name }),
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
        {/* Not SelectField: its placeholder is disabled, and "unlinked" must stay a value the admin can pick. */}
        <FieldShell id={selectId} label="Link to golfer" error={undefined} width="max-w-sm">
          <select
            id={selectId}
            value={draftGolferId}
            disabled={disabled || pending}
            onChange={(e) => setPickedGolferId(e.target.value)}
            className={`${FIELD_CLASS} disabled:opacity-50`}
          >
            <option value="">— unlinked —</option>
            {sortedGolfers.map((golfer) => (
              <option key={golfer.id} value={golfer.id}>
                {golfer.lastName}, {golfer.firstName}
              </option>
            ))}
          </select>
        </FieldShell>
        {errorMessage ? (
          <p role="alert" className="text-semantic-danger-strong text-xs">
            {errorMessage}
          </p>
        ) : null}
        {!disabled ? (
          creating ? (
            <NewGolferInlineForm
              competitor={competitor}
              pending={createMutation.isPending}
              onSubmit={(name) => createMutation.mutate(name)}
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
  competitor: TournamentCompetitorView;
  pending: boolean;
  onSubmit: (name: string) => void;
  onCancel: () => void;
}

function NewGolferInlineForm({ competitor, pending, onSubmit, onCancel }: NewGolferInlineFormProps) {
  const [editedName, setEditedName] = useState<string | null>(null);
  const name = editedName ?? competitor.name;
  const trimmed = name.trim();

  return (
    <div className="border border-hairline rounded p-2 space-y-2 bg-surface-raised/50">
      <p className="text-xs text-ink-muted">
        Creates a new golfer with no ESPN id and pins this competitor to them. Give a first and a last name.
      </p>
      <TextField
        id={`new-golfer-name-${competitor.espnCompetitorId}`}
        label="Golfer name"
        value={name}
        error={undefined}
        width="max-w-sm"
        onChange={setEditedName}
      />
      <div className="flex gap-2 items-center">
        <button
          type="button"
          disabled={trimmed.length === 0 || pending}
          onClick={() => onSubmit(trimmed)}
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

export default PlayerLinksPanel;
