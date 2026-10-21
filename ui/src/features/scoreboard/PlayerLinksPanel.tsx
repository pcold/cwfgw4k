import { useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreOwnerChange, queryKeys } from '@/shared/api/queryKeys';
import Button from '@/shared/components/Button';
import Chip from '@/shared/components/Chip';
import FieldShell from '@/shared/components/FieldShell';
import TextField from '@/shared/components/TextField';
import { FIELD_CLASS, TAP_TARGET_CLASS } from '@/shared/components/formControls';
import ModalShell from '@/shared/components/ModalShell';
import QueryState from '@/shared/components/QueryState';
import { mutationError } from '@/shared/util/mutationError';
import type {
  Golfer,
  TournamentCompetitorListing,
  TournamentCompetitorView,
} from '@/shared/api/types';

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
  // Null until picked, and again once a write's refetch lands, so the select shows the server's link.
  const [pickedGolferId, setPickedGolferId] = useState<string | null>(null);
  const draftGolferId = pickedGolferId ?? competitor.linkedGolfer?.id ?? '';
  const [creating, setCreating] = useState(false);

  // The live overlay resolves ESPN competitors through these links on the server, so every read
  // that can carry the overlay is affected, not only the scoreboard's own report.
  async function invalidateAffected(): Promise<void> {
    invalidateAfterScoreOwnerChange(queryClient, { type: 'season', seasonId });
    // A link can backfill the golfer's ESPN id, which the data-health checks read.
    void queryClient.invalidateQueries({ queryKey: queryKeys.dataHealth.root });
    await queryClient.invalidateQueries({ queryKey: queryKeys.tournamentCompetitors.byTournament(tournamentId) });
    setPickedGolferId(null);
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
    onSuccess: async () => {
      setCreating(false);
      void queryClient.invalidateQueries({ queryKey: queryKeys.golfers.list() });
      await invalidateAffected();
    },
  });

  const errorMessage = mutationError(
    saveMutation.error ?? clearMutation.error ?? createMutation.error,
  );
  const pending = saveMutation.isPending || clearMutation.isPending || createMutation.isPending;
  const hasChange = draftGolferId !== '' && draftGolferId !== competitor.linkedGolfer?.id;

  const linkedGolfer = competitor.linkedGolfer;
  const sortedGolfers = useMemo(() => {
    // The list leaves tombstones out, but a row may still link one, and the select must not read it as unlinked.
    const choices = linkedGolfer && !golfers.some((g) => g.id === linkedGolfer.id) ? [...golfers, linkedGolfer] : [...golfers];
    return choices.sort((a, b) => {
      const last = a.lastName.localeCompare(b.lastName);
      return last !== 0 ? last : a.firstName.localeCompare(b.firstName);
    });
  }, [golfers, linkedGolfer]);

  const selectId = `golfer-select-${competitor.espnCompetitorId}`;

  return (
    <li className="py-3 grid gap-2 sm:grid-cols-[1fr_auto] sm:items-center">
      <div className="space-y-1">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-ink-strong text-sm font-medium">{competitor.name}</span>
          <span className="text-xs text-ink-muted">pos {competitor.position}</span>
          {competitor.isTeamPartner ? <Chip variant="neutral">partner</Chip> : null}
          {competitor.hasOverride ? <Chip variant="neutral">manual override</Chip> : null}
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
              className={`text-ink-muted hover:text-ink-strong text-xs underline disabled:opacity-50 ${TAP_TARGET_CLASS}`}
            >
              + Create new golfer & link
            </button>
          )
        ) : null}
      </div>
      <div className="flex flex-col gap-1 sm:items-end">
        <Button
          tone="secondary"
          size="compact"
          disabled={disabled || pending || !hasChange}
          onClick={() => saveMutation.mutate(draftGolferId)}
        >
          {saveMutation.isPending ? 'Saving…' : 'Save link'}
        </Button>
        {competitor.hasOverride ? (
          <button
            type="button"
            disabled={disabled || pending}
            onClick={() => clearMutation.mutate()}
            className={`text-ink-muted hover:text-semantic-danger-hover text-xs disabled:opacity-50 ${TAP_TARGET_CLASS}`}
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
        disabled={pending}
        onChange={setEditedName}
      />
      <div className="flex gap-2 items-center">
        <Button
          tone="primary"
          size="compact"
          disabled={trimmed.length === 0 || pending}
          onClick={() => onSubmit(trimmed)}
        >
          {pending ? 'Creating…' : 'Create & link'}
        </Button>
        <button
          type="button"
          disabled={pending}
          onClick={onCancel}
          className={`text-ink-muted hover:text-ink-strong text-xs disabled:opacity-50 ${TAP_TARGET_CLASS}`}
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

export default PlayerLinksPanel;
