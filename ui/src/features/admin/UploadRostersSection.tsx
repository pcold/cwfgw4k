import { useMemo, useState, type ReactNode } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { invalidateAfterScoreOwnerChange, queryKeys } from '@/shared/api/queryKeys';
import type {
  GolferAssignment,
  GolferCandidate,
  League,
  RosterConfirmResult,
  RosterConfirmTeamInput,
  RosterPreview,
  RosterPreviewPick,
  Season,
} from '@/shared/api/types';
import { FIELD_CLASS, FieldShell, SelectField } from '@/shared/components/formFields';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';

function pickKey(teamNumber: number, pick: RosterPreviewPick): string {
  return `${teamNumber}|${pick.round}|${pick.playerName}`;
}

// An ambiguous pick's selection is a candidateValue, or '' / 'none' for
// "create new". Sentinels rather than null because <select> values are strings.
const CREATE_NEW = 'none';

const ROSTER_FILE_INPUT_CLASS =
  'block w-full text-sm text-ink-secondary file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 ' +
  'file:bg-surface-raised file:text-ink hover:file:bg-surface-raised-hover';

const PREVIEW_BUTTON_CLASS =
  'bg-semantic-info-fill hover:bg-semantic-info-fill-hover disabled:bg-disabled text-on-fill ' +
  'px-4 py-2 rounded text-sm font-medium';

// Prefixed by source, since a golfer id and an ESPN id share no namespace.
function candidateValue(candidate: GolferCandidate): string {
  return candidate.type === 'existing' ? `existing:${candidate.golferId}` : `espn:${candidate.espnId}`;
}

function candidateLabel(candidate: GolferCandidate): string {
  return candidate.type === 'existing' ? candidate.name : `${candidate.name} (new from ESPN)`;
}

export function buildConfirmTeams(
  preview: RosterPreview,
  selections: Record<string, string>,
): RosterConfirmTeamInput[] {
  return preview.teams.map((team) => ({
    teamNumber: team.teamNumber,
    teamName: team.teamName,
    picks: team.picks.map((pick) => ({
      round: pick.round,
      ownershipPct: pick.ownershipPct,
      assignment: assignmentFor(pick, selections[pickKey(team.teamNumber, pick)] ?? ''),
    })),
  }));
}

function assignmentFor(pick: RosterPreviewPick, selected: string): GolferAssignment {
  const match = pick.match;
  switch (match.type) {
    case 'matched':
      return { type: 'existing', golferId: match.golferId };
    case 'matched_espn':
    case 'matched_backfill':
      return { type: 'espn', espnId: match.espnId, name: match.golferName };
    case 'ambiguous': {
      const chosen = match.candidates.find((candidate) => candidateValue(candidate) === selected);
      return chosen ? candidateAssignment(chosen) : newGolfer(pick.playerName);
    }
    case 'no_match':
      return newGolfer(pick.playerName);
    default:
      return match satisfies never;
  }
}

function candidateAssignment(candidate: GolferCandidate): GolferAssignment {
  return candidate.type === 'existing'
    ? { type: 'existing', golferId: candidate.golferId }
    : { type: 'espn', espnId: candidate.espnId, name: candidate.name };
}

function newGolfer(playerName: string): GolferAssignment {
  const { firstName, lastName } = splitName(playerName);
  return { type: 'new', firstName, lastName };
}

function splitName(fullName: string): { firstName: string; lastName: string } {
  const trimmed = fullName.trim();
  const lastSpace = trimmed.lastIndexOf(' ');
  if (lastSpace === -1) return { firstName: trimmed, lastName: '' };
  return {
    firstName: trimmed.slice(0, lastSpace),
    lastName: trimmed.slice(lastSpace + 1),
  };
}

function UploadRostersSection(): ReactNode {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: queryKeys.leagues.root, queryFn: api.leagues });
  const [userLeagueId, setUserLeagueId] = useState<string>('');
  // User's pick wins; otherwise default to the first loaded league.
  const leagueId = userLeagueId || (leaguesQuery.data?.[0]?.id ?? '');

  const seasonsQuery = useQuery<Season[]>({
    queryKey: queryKeys.seasons.byLeague(leagueId),
    queryFn: leagueId === '' ? skipToken : () => api.seasons(leagueId),
  });

  const [userSeasonId, setUserSeasonId] = useState<string>('');
  // Same shape: user's pick wins, otherwise default to the first loaded season.
  const seasonId = userSeasonId || (seasonsQuery.data?.[0]?.id ?? '');
  const [rosterText, setRosterText] = useState<string>('');
  const [preview, setPreview] = useState<RosterPreview | null>(null);
  const [selections, setSelections] = useState<Record<string, string>>({});
  const [result, setResult] = useState<RosterConfirmResult | null>(null);

  const previewMutation = useMutation({
    mutationFn: (roster: string) => api.previewRoster(roster),
    onSuccess: (data: RosterPreview) => {
      setPreview(data);
      setSelections({});
      setResult(null);
    },
  });

  const confirmMutation = useMutation({
    mutationFn: () => {
      if (!preview) throw new Error('No preview to confirm');
      return api.confirmRoster({
        seasonId,
        teams: buildConfirmTeams(preview, selections),
      });
    },
    onSuccess: (data: RosterConfirmResult) => {
      setResult(data);
      setPreview(null);
      void queryClient.invalidateQueries({ queryKey: queryKeys.rosters.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.golfers.root });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tournamentCompetitors.root });
      invalidateAfterScoreOwnerChange(queryClient, { type: 'everySeason' });
    },
  });

  const previewError = mutationError(previewMutation.error);
  const confirmError = mutationError(confirmMutation.error);

  const setSelection = (teamNumber: number, pick: RosterPreviewPick, value: string) => {
    setSelections((prev) => ({ ...prev, [pickKey(teamNumber, pick)]: value }));
  };

  return (
    <div className="bg-surface-card rounded-lg p-6">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Upload Team Rosters
      </h3>

      {!preview && !result ? (
        <RosterInputStep
          leagues={leaguesQuery.data ?? []}
          seasons={seasonsQuery.data ?? []}
          leagueId={leagueId}
          seasonId={seasonId}
          rosterText={rosterText}
          onLeagueChange={(id) => {
            setUserLeagueId(id);
            setUserSeasonId('');
          }}
          onSeasonChange={setUserSeasonId}
          onRosterChange={setRosterText}
          onSubmit={() => previewMutation.mutate(rosterText)}
          submitting={previewMutation.isPending}
          errorMessage={previewError}
        />
      ) : null}

      {preview ? (
        <RosterReviewStep
          preview={preview}
          selections={selections}
          onSelectionChange={setSelection}
          onConfirm={() => confirmMutation.mutate()}
          onBack={() => {
            setPreview(null);
            setResult(null);
          }}
          confirming={confirmMutation.isPending}
          errorMessage={confirmError}
        />
      ) : null}

      {result ? <RosterResultView result={result} /> : null}
    </div>
  );
}

interface InputStepProps {
  leagues: League[];
  seasons: Season[];
  leagueId: string;
  seasonId: string;
  rosterText: string;
  onLeagueChange: (id: string) => void;
  onSeasonChange: (id: string) => void;
  onRosterChange: (text: string) => void;
  onSubmit: () => void;
  submitting: boolean;
  errorMessage: string | null;
}

function RosterInputStep({
  leagues,
  seasons,
  leagueId,
  seasonId,
  rosterText,
  onLeagueChange,
  onSeasonChange,
  onRosterChange,
  onSubmit,
  submitting,
  errorMessage,
}: InputStepProps) {
  const disabled = submitting || !rosterText.trim() || !seasonId;

  return (
    <div>
      <div className="flex flex-wrap gap-4 mb-4">
        <FieldShell id="roster-league" label="League" error={undefined} width="sm:w-64">
          <select
            id="roster-league"
            value={leagueId}
            onChange={(e) => onLeagueChange(e.target.value)}
            className={FIELD_CLASS}
          >
            {leagues.map((lg) => (
              <option key={lg.id} value={lg.id}>
                {lg.name}
              </option>
            ))}
          </select>
        </FieldShell>
        <SelectField
          id="roster-season"
          label="Season"
          value={seasonId}
          error={undefined}
          placeholder="Select a season"
          options={seasons.map((s) => ({ value: s.id, label: seasonLabel(s) }))}
          width="sm:w-64"
          onChange={onSeasonChange}
        />
      </div>

      <div className="mb-4">
        <FieldShell
          id="roster-file"
          label="Roster file (.tsv or .csv with the header row team_number,team_name,round,player_name,ownership_pct)"
          error={undefined}
        >
          <input
            id="roster-file"
            type="file"
            accept=".tsv,.csv,text/tab-separated-values,text/csv,text/plain"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              void file.text().then(onRosterChange);
            }}
            className={ROSTER_FILE_INPUT_CLASS}
          />
        </FieldShell>
        {rosterText ? (
          <p className="mt-2 text-xs text-ink-muted">
            Loaded {rosterText.split('\n').filter((line) => line.trim().length > 0).length} non-empty lines.
          </p>
        ) : null}
      </div>

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={onSubmit}
          disabled={disabled}
          className={PREVIEW_BUTTON_CLASS}
        >
          {submitting ? 'Matching...' : 'Match Players with ESPN'}
        </button>
        {errorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>
    </div>
  );
}

interface ReviewStepProps {
  preview: RosterPreview;
  selections: Record<string, string>;
  onSelectionChange: (teamNumber: number, pick: RosterPreviewPick, value: string) => void;
  onConfirm: () => void;
  onBack: () => void;
  confirming: boolean;
  errorMessage: string | null;
}

function RosterReviewStep({
  preview,
  selections,
  onSelectionChange,
  onConfirm,
  onBack,
  confirming,
  errorMessage,
}: ReviewStepProps) {
  const summary = useMemo(
    () => [
      { label: 'Total Picks', value: preview.totalPicks, color: 'text-ink-strong' },
      { label: 'Exact Matches', value: preview.matchedCount, color: 'text-semantic-success' },
      preview.ambiguousCount > 0
        ? { label: 'Ambiguous', value: preview.ambiguousCount, color: 'text-semantic-warning' }
        : null,
      preview.unmatchedCount > 0
        ? { label: 'No Match', value: preview.unmatchedCount, color: 'text-semantic-danger' }
        : null,
    ],
    [preview],
  );

  return (
    <div>
      <div className="flex gap-6 mb-4 text-sm">
        {summary
          .filter((s): s is { label: string; value: number; color: string } => s !== null)
          .map((s) => (
            <div key={s.label}>
              <span className="text-ink-muted">{s.label}:</span>
              <span className={`${s.color} font-bold ml-1`}>{s.value}</span>
            </div>
          ))}
      </div>

      {preview.teams.map((team) => (
        <div key={`${team.teamNumber}-${team.teamName}`} className="mb-4">
          <div className="text-sm font-bold text-ink-body mb-1">
            <span className="text-ink-muted">#{team.teamNumber}</span>
            <span className="ml-1 uppercase">{team.teamName}</span>
          </div>
          <table className="w-full text-xs">
            <tbody>
              {team.picks.map((pick) => {
                const key = pickKey(team.teamNumber, pick);
                const match = pick.match;
                return (
                  <tr key={key} className="border-t border-hairline">
                    <td className="px-2 py-1.5 text-ink-muted w-8">R{pick.round}</td>
                    <td className="px-2 py-1.5 font-mono">{pick.playerName}</td>
                    <td className="px-2 py-1.5 text-center w-10">
                      {pick.ownershipPct < 100 ? `${pick.ownershipPct}%` : null}
                    </td>
                    <td className="px-2 py-1.5 w-6 text-center">
                      {match.type === 'matched' ||
                      match.type === 'matched_espn' ||
                      match.type === 'matched_backfill' ? (
                        <span className="text-semantic-success" aria-label="exact match">
                          ✓
                        </span>
                      ) : null}
                      {match.type === 'ambiguous' ? (
                        <span className="text-semantic-warning" aria-label="ambiguous match">
                          ?
                        </span>
                      ) : null}
                      {match.type === 'no_match' ? (
                        <span className="text-semantic-danger" aria-label="no match">
                          ✗
                        </span>
                      ) : null}
                    </td>
                    <td className="px-2 py-1.5">
                      {match.type === 'matched' ? (
                        <span className="text-semantic-success">{match.golferName}</span>
                      ) : null}
                      {match.type === 'matched_espn' ? (
                        <>
                          <span className="text-semantic-success">{match.golferName}</span>
                          <span className="ml-1 text-ink-muted">(new from ESPN)</span>
                        </>
                      ) : null}
                      {match.type === 'matched_backfill' ? (
                        <>
                          <span className="text-semantic-success">{match.golferName}</span>
                          <span className="ml-1 text-ink-muted">(ESPN id added on confirm)</span>
                        </>
                      ) : null}
                      {match.type === 'ambiguous' ? (
                        <select
                          aria-label={`Golfer match for ${pick.playerName}`}
                          value={selections[key] ?? ''}
                          onChange={(e) =>
                            onSelectionChange(team.teamNumber, pick, e.target.value)
                          }
                          className="bg-surface-raised border border-semantic-warning-border rounded px-2 py-1 text-xs"
                        >
                          <option value="">-- select player --</option>
                          {match.candidates.map((c) => (
                            <option key={candidateValue(c)} value={candidateValue(c)}>
                              {candidateLabel(c)}
                            </option>
                          ))}
                          <option value={CREATE_NEW}>None of these (create new)</option>
                        </select>
                      ) : null}
                      {match.type === 'no_match' ? (
                        <span className="text-semantic-danger text-xs">Will create new golfer</span>
                      ) : null}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ))}

      <div className="flex items-center gap-4 mt-4">
        <button
          type="button"
          onClick={onConfirm}
          disabled={confirming}
          className="bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary px-4 py-2 rounded text-sm font-medium"
        >
          {confirming ? 'Creating...' : 'Confirm & Create Teams'}
        </button>
        <button
          type="button"
          onClick={onBack}
          className="bg-secondary hover:bg-secondary-hover text-on-fill px-4 py-2 rounded text-sm font-medium"
        >
          Back
        </button>
        {errorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function RosterResultView({ result }: { result: RosterConfirmResult }) {
  return (
    <div className="mt-6 pt-4 border-t border-hairline">
      <h4 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Rosters Created
      </h4>
      <div className="flex gap-6 mb-4 text-sm">
        <div>
          <span className="text-ink-muted">Teams:</span>
          <span className="text-semantic-success font-bold ml-1">{result.teamsCreated}</span>
        </div>
        <div>
          <span className="text-ink-muted">New Golfers:</span>
          <span className="text-semantic-info font-bold ml-1">{result.golfersCreated}</span>
        </div>
      </div>
      <div className="flex flex-wrap gap-2 text-xs">
        {result.teams.map((team) => (
          <span key={team.id} className="bg-surface-raised px-2 py-1 rounded">
            <span className="text-ink-muted">#{team.teamNumber}</span>
            <span className="ml-1 uppercase">{team.teamName}</span>
          </span>
        ))}
      </div>
    </div>
  );
}

export default UploadRostersSection;
