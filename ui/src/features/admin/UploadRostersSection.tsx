import { useMemo, useState } from 'react';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type {
  League,
  RosterConfirmResult,
  RosterConfirmTeamInput,
  RosterPreview,
  RosterPreviewPick,
  Season,
} from '@/shared/api/types';
import { mutationError } from '@/shared/util/mutationError';
import { seasonLabel } from '@/shared/util/season';

function pickKey(teamNumber: number, pick: RosterPreviewPick): string {
  return `${teamNumber}|${pick.round}|${pick.inputName}`;
}

function initialSelections(preview: RosterPreview): Record<string, string> {
  const out: Record<string, string> = {};
  for (const team of preview.teams) {
    for (const pick of team.picks) {
      const key = pickKey(team.teamNumber, pick);
      if (pick.matchStatus === 'exact' && pick.espnId && pick.espnName) {
        out[key] = `${pick.espnId}|${pick.espnName}`;
      } else {
        out[key] = '';
      }
    }
  }
  return out;
}

export function buildConfirmTeams(
  preview: RosterPreview,
  selections: Record<string, string>,
): RosterConfirmTeamInput[] {
  return preview.teams.map((team) => ({
    teamNumber: team.teamNumber,
    teamName: team.teamName,
    picks: team.picks.map((pick) => {
      const selected = selections[pickKey(team.teamNumber, pick)] ?? '';
      let espnId: string | null = null;
      let espnName: string | null = null;
      if (selected && selected !== 'none') {
        const [id, ...rest] = selected.split('|');
        espnId = id;
        espnName = rest.join('|') || null;
      }
      return {
        round: pick.round,
        playerName: pick.inputName,
        ownershipPct: pick.ownershipPct,
        espnId,
        espnName,
      };
    }),
  }));
}

function UploadRostersSection() {
  const queryClient = useQueryClient();

  const leaguesQuery = useQuery<League[]>({ queryKey: ['leagues'], queryFn: api.leagues });
  const [userLeagueId, setUserLeagueId] = useState<string>('');
  // User's pick wins; otherwise default to the first loaded league.
  const leagueId = userLeagueId || (leaguesQuery.data?.[0]?.id ?? '');

  const seasonsQuery = useQuery<Season[]>({
    queryKey: ['seasons', leagueId],
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
      setSelections(initialSelections(data));
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
      void queryClient.invalidateQueries({ queryKey: ['rosters'] });
    },
  });

  const previewError = mutationError(previewMutation.error);
  const confirmError = mutationError(confirmMutation.error);

  const setSelection = (teamNumber: number, pick: RosterPreviewPick, value: string) => {
    setSelections((prev) => ({ ...prev, [pickKey(teamNumber, pick)]: value }));
  };

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
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
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="roster-league">
            League
          </label>
          <select
            id="roster-league"
            value={leagueId}
            onChange={(e) => onLeagueChange(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-64"
          >
            {leagues.map((lg) => (
              <option key={lg.id} value={lg.id}>
                {lg.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1" htmlFor="roster-season">
            Season
          </label>
          <select
            id="roster-season"
            value={seasonId}
            onChange={(e) => onSeasonChange(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-64"
          >
            <option value="" disabled>
              Select a season
            </option>
            {seasons.map((s) => (
              <option key={s.id} value={s.id}>
                {seasonLabel(s)}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="mb-4">
        <label className="block text-xs text-gray-400 mb-1" htmlFor="roster-file">
          Roster file (.tsv or .csv with the header row team_number,team_name,round,player_name,ownership_pct)
        </label>
        <input
          id="roster-file"
          type="file"
          accept=".tsv,.csv,text/tab-separated-values,text/csv,text/plain"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) return;
            void file.text().then(onRosterChange);
          }}
          className="block w-full text-sm text-gray-300 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:bg-gray-700 file:text-gray-100 hover:file:bg-gray-600"
        />
        {rosterText ? (
          <p className="mt-2 text-xs text-gray-400">
            Loaded {rosterText.split('\n').filter((line) => line.trim().length > 0).length} non-empty lines.
          </p>
        ) : null}
      </div>

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={onSubmit}
          disabled={disabled}
          className="bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {submitting ? 'Matching...' : 'Match Players with ESPN'}
        </button>
        {errorMessage ? (
          <span role="alert" className="text-red-400 text-sm">
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
      { label: 'Total Picks', value: preview.totalPicks, color: 'text-white' },
      { label: 'Exact Matches', value: preview.exactMatches, color: 'text-green-400' },
      preview.ambiguous > 0
        ? { label: 'Ambiguous', value: preview.ambiguous, color: 'text-yellow-400' }
        : null,
      preview.noMatch > 0
        ? { label: 'No Match', value: preview.noMatch, color: 'text-red-400' }
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
              <span className="text-gray-400">{s.label}:</span>
              <span className={`${s.color} font-bold ml-1`}>{s.value}</span>
            </div>
          ))}
      </div>

      {preview.teams.map((team) => (
        <div key={`${team.teamNumber}-${team.teamName}`} className="mb-4">
          <div className="text-sm font-bold text-gray-200 mb-1">
            <span className="text-gray-400">#{team.teamNumber}</span>
            <span className="ml-1 uppercase">{team.teamName}</span>
          </div>
          <table className="w-full text-xs">
            <tbody>
              {team.picks.map((pick) => {
                const key = pickKey(team.teamNumber, pick);
                return (
                  <tr key={key} className="border-t border-gray-700">
                    <td className="px-2 py-1.5 text-gray-400 w-8">R{pick.round}</td>
                    <td className="px-2 py-1.5 font-mono">{pick.inputName}</td>
                    <td className="px-2 py-1.5 text-center w-10">
                      {pick.ownershipPct < 100 ? `${pick.ownershipPct}%` : null}
                    </td>
                    <td className="px-2 py-1.5 w-6 text-center">
                      {pick.matchStatus === 'exact' ? (
                        <span className="text-green-400" aria-label="exact match">
                          ✓
                        </span>
                      ) : null}
                      {pick.matchStatus === 'ambiguous' ? (
                        <span className="text-yellow-400" aria-label="ambiguous match">
                          ?
                        </span>
                      ) : null}
                      {pick.matchStatus === 'no_match' ? (
                        <span className="text-red-400" aria-label="no match">
                          ✗
                        </span>
                      ) : null}
                    </td>
                    <td className="px-2 py-1.5">
                      {pick.matchStatus === 'exact' ? (
                        <span className="text-green-400">{pick.espnName}</span>
                      ) : null}
                      {pick.matchStatus === 'ambiguous' ? (
                        <select
                          aria-label={`ESPN match for ${pick.inputName}`}
                          value={selections[key] ?? ''}
                          onChange={(e) =>
                            onSelectionChange(team.teamNumber, pick, e.target.value)
                          }
                          className="bg-gray-700 border border-yellow-600 rounded px-2 py-1 text-xs"
                        >
                          <option value="">-- select player --</option>
                          {pick.suggestions.map((s) => (
                            <option key={s.espnId} value={`${s.espnId}|${s.name}`}>
                              {s.name} ({s.espnId})
                            </option>
                          ))}
                          <option value="none">None of these (create new)</option>
                        </select>
                      ) : null}
                      {pick.matchStatus === 'no_match' ? (
                        <span className="text-red-400 text-xs">Will create new golfer</span>
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
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {confirming ? 'Creating...' : 'Confirm & Create Teams'}
        </button>
        <button
          type="button"
          onClick={onBack}
          className="bg-gray-600 hover:bg-gray-500 text-white px-4 py-2 rounded text-sm font-medium"
        >
          Back
        </button>
        {errorMessage ? (
          <span role="alert" className="text-red-400 text-sm">
            {errorMessage}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function RosterResultView({ result }: { result: RosterConfirmResult }) {
  return (
    <div className="mt-6 pt-4 border-t border-gray-700">
      <h4 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Rosters Created
      </h4>
      <div className="flex gap-6 mb-4 text-sm">
        <div>
          <span className="text-gray-400">Teams:</span>
          <span className="text-green-400 font-bold ml-1">{result.teamsCreated}</span>
        </div>
        <div>
          <span className="text-gray-400">New Golfers:</span>
          <span className="text-blue-400 font-bold ml-1">{result.golfersCreated}</span>
        </div>
      </div>
      {result.teams.map((team) => (
        <div key={team.teamId} className="mb-3">
          <div className="text-sm font-bold text-gray-200 mb-1">
            <span className="text-gray-400">#{team.teamNumber}</span>
            <span className="ml-1 uppercase">{team.teamName}</span>
          </div>
          <div className="flex flex-wrap gap-2 text-xs">
            {team.picks.map((pick) => (
              <span key={pick.golferId} className="bg-gray-700 px-2 py-1 rounded">
                <span className="text-gray-400">R{pick.round}</span>
                <span className="ml-1">{pick.golferName}</span>
                {pick.ownershipPct < 100 ? (
                  <span className="text-gray-500 ml-1">{pick.ownershipPct}%</span>
                ) : null}
              </span>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export default UploadRostersSection;
