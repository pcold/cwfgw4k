import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { QueryState } from '@/shared/components/QueryState';
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

const competitorsKey = (tournamentId: string) =>
  ['tournamentCompetitors', tournamentId] as const;

function PlayerLinksPanel({ tournamentId, seasonId, onClose }: PlayerLinksPanelProps) {
  const competitorsQuery = useQuery({
    queryKey: competitorsKey(tournamentId),
    queryFn: () => api.tournamentCompetitors(tournamentId),
  });
  const golfersQuery = useQuery({
    queryKey: ['golfers', 'all'] as const,
    queryFn: () => api.golfers(),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <div
      role="dialog"
      aria-label="Manage player links"
      className="fixed inset-0 z-40 flex items-start justify-center bg-black/60 p-4 overflow-y-auto"
    >
      <div className="bg-gray-900 border border-gray-700 rounded-lg shadow-xl max-w-3xl w-full my-8">
        <header className="flex items-center justify-between border-b border-gray-700 px-4 py-3">
          <h2 className="text-lg font-semibold text-white">Manage player links</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-white text-sm"
            aria-label="Close panel"
          >
            Close
          </button>
        </header>
        <div className="p-4 space-y-4">
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
      </div>
    </div>
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
      <p className="text-gray-400 text-sm">
        No ESPN competitors found for this tournament.
      </p>
    );
  }
  return (
    <>
      {listing.isFinalized ? (
        <p
          role="status"
          className="text-yellow-300 text-xs bg-yellow-900/30 border border-yellow-700 rounded px-3 py-2"
        >
          Links locked — tournament finalized.
        </p>
      ) : null}
      <ul className="divide-y divide-gray-700">
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

  function invalidateAffected() {
    void queryClient.invalidateQueries({ queryKey: competitorsKey(tournamentId) });
    void queryClient.invalidateQueries({ queryKey: ['tournamentReport', seasonId] });
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
      void queryClient.invalidateQueries({ queryKey: ['golfers', 'all'] });
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
          <span className="text-white text-sm font-medium">{competitor.name}</span>
          <span className="text-xs text-gray-400">pos {competitor.position}</span>
          {competitor.isTeamPartner ? (
            <span className="text-xs uppercase tracking-wider bg-gray-700 text-gray-200 rounded px-1.5 py-0.5">
              partner
            </span>
          ) : null}
          {competitor.hasOverride ? (
            <span className="text-xs uppercase tracking-wider bg-blue-700 text-blue-100 rounded px-1.5 py-0.5">
              manual override
            </span>
          ) : null}
        </div>
        <label htmlFor={selectId} className="block text-xs text-gray-400">
          Link to golfer
        </label>
        <select
          id={selectId}
          value={draftGolferId}
          disabled={disabled || pending}
          onChange={(e) => setDraftGolferId(e.target.value)}
          className="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm w-full max-w-sm disabled:opacity-50"
        >
          <option value="">— unlinked —</option>
          {sortedGolfers.map((golfer) => (
            <option key={golfer.id} value={golfer.id}>
              {golfer.lastName}, {golfer.firstName}
              {golfer.active ? '' : ' (inactive)'}
            </option>
          ))}
        </select>
        {errorMessage ? (
          <p role="alert" className="text-red-300 text-xs">
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
              className="text-blue-400 hover:text-blue-300 text-xs underline disabled:opacity-50"
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
          className="bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 text-white text-xs px-3 py-1 rounded font-medium"
        >
          {saveMutation.isPending ? 'Saving…' : 'Save link'}
        </button>
        {competitor.hasOverride ? (
          <button
            type="button"
            disabled={disabled || pending}
            onClick={() => clearMutation.mutate()}
            className="text-gray-400 hover:text-red-300 text-xs disabled:opacity-50"
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
    <div className="border border-gray-700 rounded p-2 space-y-2 bg-gray-800/50">
      <p className="text-xs text-gray-400">
        Creates a new golfer with no ESPN id and pins this competitor to them.
      </p>
      <div className="flex gap-2 flex-wrap">
        <label className="text-xs text-gray-400 flex flex-col">
          First name
          <input
            type="text"
            value={firstName}
            disabled={pending}
            onChange={(e) => setFirstName(e.target.value)}
            className="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white disabled:opacity-50"
          />
        </label>
        <label className="text-xs text-gray-400 flex flex-col">
          Last name
          <input
            type="text"
            value={lastName}
            disabled={pending}
            onChange={(e) => setLastName(e.target.value)}
            className="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white disabled:opacity-50"
          />
        </label>
      </div>
      <div className="flex gap-2 items-center">
        <button
          type="button"
          disabled={!valid || pending}
          onClick={() => onSubmit({ firstName: firstName.trim(), lastName: lastName.trim() })}
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white text-xs px-3 py-1 rounded font-medium"
        >
          {pending ? 'Creating…' : 'Create & link'}
        </button>
        <button
          type="button"
          disabled={pending}
          onClick={onCancel}
          className="text-gray-400 hover:text-white text-xs disabled:opacity-50"
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
