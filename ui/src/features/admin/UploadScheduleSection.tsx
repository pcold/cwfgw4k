import { useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type {
  ConfirmedTournamentEntry,
  PreviewedTournament,
  Season,
  SeasonImportResult,
  SeasonSchedulePreviewResult,
  Tournament,
} from '@/shared/api/types';
import Button from '@/shared/components/Button';
import { DateField, FieldShell, SelectField, TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';
import { AdminLeagueField, useAdminLeague } from './AdminLeagueField';

const SKIPPED_BANNER_CLASS =
  'mb-4 px-3 py-2 bg-semantic-warning-tint/30 border border-semantic-warning-soft rounded ' +
  'text-semantic-warning-ink text-xs';

function toConfirmedEntry(entry: PreviewedTournament): ConfirmedTournamentEntry {
  return { espnEventId: entry.espnEventId, name: entry.name, startDate: entry.startDate, endDate: entry.endDate };
}

function UploadScheduleSection(): ReactNode {
  const queryClient = useQueryClient();

  const { leagues, leagueId, setLeagueId } = useAdminLeague();

  const seasonsQuery = useQuery<Season[]>({
    queryKey: queryKeys.seasons.byLeague(leagueId),
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });

  const [seasonId, setSeasonId] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  // Candidates from the ESPN calendar, not yet written to the DB. The
  // operator drops rows that aren't part of the season (e.g. the
  // Presidents Cup) before confirming. Held as the whole result object
  // (rather than separate arrays) so the review panel stays visible —
  // keyed off "a preview ran", not "entries remain" — even after the
  // operator removes the last candidate.
  const [previewResult, setPreviewResult] = useState<SeasonSchedulePreviewResult | null>(null);

  // Tournaments returned by the confirm POST. Stored locally so we can edit
  // each row's multiplier in place and patch them via PUT one at a time.
  const [createdTournaments, setCreatedTournaments] = useState<Tournament[]>([]);
  const [skippedSummary, setSkippedSummary] = useState<SeasonImportResult['skipped']>([]);

  const previewMutation = useMutation({
    mutationFn: () => api.previewSeasonSchedule({ seasonId, startDate, endDate }),
    onSuccess: (result) => {
      setPreviewResult(result);
      setCreatedTournaments([]);
      setSkippedSummary([]);
    },
  });

  const confirmMutation = useMutation({
    mutationFn: () =>
      api.confirmSeasonSchedule({
        seasonId,
        entries: (previewResult?.entries ?? []).map(toConfirmedEntry),
      }),
    onSuccess: (result) => {
      setCreatedTournaments(result.created);
      setSkippedSummary(result.skipped);
      setPreviewResult(null);
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournaments.root });
    },
  });

  const previewErrorMessage = mutationError(previewMutation.error);
  const confirmErrorMessage = mutationError(confirmMutation.error);

  const previewDisabled =
    previewMutation.isPending || !seasonId || !startDate || !endDate || startDate > endDate;

  const removePreviewEntry = (espnEventId: string) =>
    setPreviewResult((prev) =>
      prev ? { ...prev, entries: prev.entries.filter((entry) => entry.espnEventId !== espnEventId) } : prev,
    );

  return (
    <div className="bg-surface-card rounded-lg p-6">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Import Season Schedule from ESPN
      </h3>
      <p className="text-xs text-ink-muted mb-4">
        Pulls the PGA tour calendar from ESPN for the date range and previews a tournament for
        every event whose start date falls inside it — nothing is created yet. Remove any events
        that aren&apos;t part of this league&apos;s season (e.g. the Presidents Cup), then confirm.
        Week labels (1, 2, 3a, 3b…) are assigned chronologically over whatever you keep, so
        removing an event closes its numbering gap. Re-running on a season skips events already
        linked. After confirming, edit the per-tournament payout multiplier inline and Save each
        row to push the change.
      </p>

      <div className="flex flex-wrap gap-4 mb-4">
        <AdminLeagueField
          id="schedule-league"
          leagues={leagues}
          leagueId={leagueId}
          onChange={(id) => {
            setLeagueId(id);
            setSeasonId('');
          }}
        />
        <SelectField
          id="schedule-season"
          label="Season"
          value={seasonId}
          error={undefined}
          placeholder="Select a season"
          options={(seasonsQuery.data ?? []).map((s) => ({ value: s.id, label: seasonLabel(s) }))}
          width="sm:w-64"
          onChange={setSeasonId}
        />
        <DateField id="schedule-start" label="Start date" value={startDate} error={undefined} onChange={setStartDate} />
        <DateField id="schedule-end" label="End date" value={endDate} error={undefined} onChange={setEndDate} />
      </div>

      <div className="flex items-center gap-4">
        <Button tone="primary" size="regular" onClick={() => previewMutation.mutate()} disabled={previewDisabled}>
          {previewMutation.isPending ? 'Fetching…' : 'Preview Import'}
        </Button>
        {previewErrorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {previewErrorMessage}
          </span>
        ) : null}
      </div>

      {previewResult ? (
        <SchedulePreview
          entries={previewResult.entries}
          skipped={previewResult.skipped}
          onRemove={removePreviewEntry}
          onConfirm={() => confirmMutation.mutate()}
          confirmPending={confirmMutation.isPending}
          confirmErrorMessage={confirmErrorMessage}
        />
      ) : null}

      {createdTournaments.length > 0 || skippedSummary.length > 0 ? (
        <ImportResult
          created={createdTournaments}
          skipped={skippedSummary}
          onRowSaved={(updated) =>
            setCreatedTournaments((prev) =>
              prev.map((t) => (t.id === updated.id ? updated : t)),
            )
          }
        />
      ) : null}
    </div>
  );
}

interface SchedulePreviewProps {
  entries: PreviewedTournament[];
  skipped: SeasonImportResult['skipped'];
  onRemove: (espnEventId: string) => void;
  onConfirm: () => void;
  confirmPending: boolean;
  confirmErrorMessage: string | null;
}

function SchedulePreview({
  entries,
  skipped,
  onRemove,
  onConfirm,
  confirmPending,
  confirmErrorMessage,
}: SchedulePreviewProps) {
  return (
    <div className="mt-6 pt-4 border-t border-hairline">
      <h4 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Review Before Confirming
      </h4>

      {skipped.length > 0 ? (
        <div className={SKIPPED_BANNER_CLASS}>
          <div className="font-semibold mb-1">Skipped ESPN entries:</div>
          <ul className="space-y-0.5">
            {skipped.map((s) => (
              <li key={s.espnEventId}>
                <span className="font-mono">{s.espnEventName}</span> — {s.reason}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {entries.length > 0 ? (
        <table className="w-full text-sm mb-4">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr>
              <th className="px-3 py-2 text-left">Wk</th>
              <th className="px-3 py-2 text-left">Tournament</th>
              <th className="px-3 py-2 text-left">Dates</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.espnEventId} className="border-t border-hairline">
                <td className="px-3 py-2 text-ink-muted font-mono">{entry.week}</td>
                <td className="px-3 py-2 font-medium">{entry.name}</td>
                <td className="px-3 py-2 text-ink-muted text-xs">
                  {entry.startDate} to {entry.endDate}
                </td>
                <td className="px-3 py-2 text-xs">
                  <button
                    type="button"
                    onClick={() => onRemove(entry.espnEventId)}
                    className={`text-semantic-danger hover:text-semantic-danger-hover ${TAP_TARGET_CLASS}`}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="text-xs text-ink-muted mb-4">
          Every candidate was removed — confirming now would create nothing.
        </p>
      )}

      <div className="flex items-center gap-4">
        <Button tone="primary" size="regular" onClick={onConfirm} disabled={confirmPending || entries.length === 0}>
          {confirmPending ? 'Confirming…' : 'Confirm Import'}
        </Button>
        {confirmErrorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {confirmErrorMessage}
          </span>
        ) : null}
      </div>
    </div>
  );
}

interface ImportResultProps {
  created: Tournament[];
  skipped: SeasonImportResult['skipped'];
  onRowSaved: (updated: Tournament) => void;
}

function ImportResult({ created, skipped, onRowSaved }: ImportResultProps) {
  return (
    <div className="mt-6 pt-4 border-t border-hairline">
      <h4 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Confirm Imported Tournaments
      </h4>

      <div className="flex gap-6 mb-4 text-sm">
        <div>
          <span className="text-ink-muted">Created:</span>
          <span className="text-semantic-success font-bold ml-1">{created.length}</span>
        </div>
        {skipped.length > 0 ? (
          <div>
            <span className="text-ink-muted">Skipped:</span>
            <span className="text-semantic-warning font-bold ml-1">{skipped.length}</span>
          </div>
        ) : null}
      </div>

      {skipped.length > 0 ? (
        <div className={SKIPPED_BANNER_CLASS}>
          <div className="font-semibold mb-1">Skipped ESPN entries:</div>
          <ul className="space-y-0.5">
            {skipped.map((s) => (
              <li key={s.espnEventId}>
                <span className="font-mono">{s.espnEventName}</span> — {s.reason}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {created.length > 0 ? (
        <table className="w-full text-sm">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr>
              <th className="px-3 py-2 text-left">Wk</th>
              <th className="px-3 py-2 text-left">Tournament</th>
              <th className="px-3 py-2 text-left">Dates</th>
              <th className="px-3 py-2 text-center">Mult</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {created.map((tournament) => (
              <TournamentRow
                key={tournament.id}
                tournament={tournament}
                onSaved={onRowSaved}
              />
            ))}
          </tbody>
        </table>
      ) : null}
    </div>
  );
}

interface TournamentRowProps {
  tournament: Tournament;
  onSaved: (updated: Tournament) => void;
}

function TournamentRow({ tournament, onSaved }: TournamentRowProps) {
  const [draftMultiplier, setDraftMultiplier] = useState<string>(String(tournament.payoutMultiplier));

  const updateMutation = useMutation({
    mutationFn: () => {
      const parsed = Number.parseFloat(draftMultiplier);
      return api.updateTournament(tournament.id, { payoutMultiplier: parsed });
    },
    onSuccess: (updated) => {
      onSaved(updated);
      setDraftMultiplier(String(updated.payoutMultiplier));
    },
  });

  const draftValue = Number.parseFloat(draftMultiplier);
  const dirty =
    draftMultiplier.trim() !== '' &&
    Number.isFinite(draftValue) &&
    draftValue !== tournament.payoutMultiplier;
  const invalid = draftMultiplier.trim() !== '' && (!Number.isFinite(draftValue) || draftValue <= 0);
  const multiplierError = invalid ? 'Enter a number greater than 0' : undefined;
  // The input hand-rolls CONTROL_CLASS's sizing: that class's border colour and this one would tie, settled
  // by stylesheet order rather than class order.
  const multiplierBorder = invalid ? 'border-semantic-danger-border' : 'border-control-border';
  const multiplierId = `multiplier-${tournament.id}`;
  const errorMessage = mutationError(updateMutation.error);

  return (
    <tr className="border-t border-hairline">
      <td className="px-3 py-2 text-ink-muted font-mono">{tournament.week ?? ''}</td>
      <td className="px-3 py-2 font-medium">{tournament.name}</td>
      <td className="px-3 py-2 text-ink-muted text-xs">
        {tournament.startDate} to {tournament.endDate}
      </td>
      <td className="px-3 py-2 text-center">
        <FieldShell
          id={multiplierId}
          label={`Payout multiplier for ${tournament.name}`}
          error={multiplierError}
          hideLabel
        >
          <input
            id={multiplierId}
            type="number"
            min="0.5"
            step="0.5"
            value={draftMultiplier}
            onChange={(e) => setDraftMultiplier(e.target.value)}
            aria-invalid={multiplierError !== undefined}
            aria-describedby={multiplierError !== undefined ? `${multiplierId}-error` : undefined}
            className={`bg-surface-raised border ${multiplierBorder} rounded px-3 py-2 text-base ${TAP_TARGET_CLASS} w-20 text-right`}
          />
        </FieldShell>
      </td>
      <td className="px-3 py-2 text-xs">
        <div className="flex items-center gap-2">
          <Button
            tone="primary"
            size="compact"
            onClick={() => updateMutation.mutate()}
            disabled={!dirty || invalid || updateMutation.isPending}
          >
            {updateMutation.isPending ? 'Saving…' : 'Save'}
          </Button>
          {errorMessage ? (
            <span role="alert" className="text-semantic-danger">
              {errorMessage}
            </span>
          ) : null}
        </div>
      </td>
    </tr>
  );
}

export default UploadScheduleSection;
