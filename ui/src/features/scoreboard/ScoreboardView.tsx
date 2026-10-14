import { useMemo, useState, type ReactNode } from 'react';
import type { WeeklyReport } from '@/shared/api/types';
import { formatMoney } from '@/shared/util/money';
import ColumnHeaderFilter, {
  type ColumnHeaderFilterOption,
} from '@/shared/components/ColumnHeaderFilter';
import {
  formatScoreToPar,
  hasPlayerOn,
  hasUndraftedPlayer,
  leaderboardTeamNames,
  playerNames,
  scoreboardTeams,
  teamLabel,
} from './scoreboardModel';

const ALL = '';
const UNDRAFTED = '__undrafted__';

function formatSigned(value: number): string {
  if (value > 0) return `+${formatMoney(value)}`;
  return formatMoney(value);
}

function rowTint(weeklyTotal: number): string {
  if (weeklyTotal > 0) return 'bg-green-900/10';
  if (weeklyTotal < 0) return 'bg-red-900/10';
  return '';
}

function signColor(value: number): string {
  if (value > 0) return 'text-green-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-400';
}

interface Props {
  report: WeeklyReport;
  finalizeSlot?: ReactNode;
  onGolferClick?: (golferId: string) => void;
}

function ScoreboardView({ report, finalizeSlot, onGolferClick }: Props): ReactNode {
  const teams = scoreboardTeams(report);
  const { tournament, weeklyTotals, liveLeaderboard: leaderboard } = report;
  const [leaderboardTeamFilter, setLeaderboardTeamFilter] = useState<string>(ALL);

  const leaderboardTeamOptions = useMemo<ColumnHeaderFilterOption[]>(
    () => [
      { value: ALL, label: 'Team' },
      ...leaderboardTeamNames(leaderboard).map((name) => ({ value: name, label: name })),
      ...(leaderboard.some(hasUndraftedPlayer) ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
    ],
    [leaderboard],
  );

  const filteredLeaderboard = useMemo(() => {
    if (leaderboardTeamFilter === ALL) return leaderboard;
    if (leaderboardTeamFilter === UNDRAFTED) return leaderboard.filter(hasUndraftedPlayer);
    return leaderboard.filter((entry) => hasPlayerOn(entry, leaderboardTeamFilter));
  }, [leaderboard, leaderboardTeamFilter]);
  const isCompleted = tournament.status === 'completed';
  const statusBanner = isCompleted
    ? 'Results finalized'
    : 'Tournament in progress — scores are live estimates';
  const bannerClass = isCompleted
    ? 'bg-green-900/30 border-green-700 text-green-300'
    : 'bg-yellow-900/40 border-yellow-700 text-yellow-300';

  return (
    <section className="space-y-6">
      <header className="border-b border-gray-700 pb-3">
        <h2 className="text-xl font-semibold text-green-400">
          {tournament.name ?? 'Scoreboard'}
        </h2>
        <p className="text-xs text-gray-400">
          Week {tournament.week ?? '?'}
          {tournament.payoutMultiplier !== 1 ? ` · ${tournament.payoutMultiplier}x payout` : ''}
        </p>
      </header>

      <div
        className={`px-4 py-2 border rounded text-sm flex items-center justify-between gap-3 ${bannerClass}`}
      >
        <span>{statusBanner}</span>
        {!isCompleted && finalizeSlot ? finalizeSlot : null}
      </div>

      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="w-full text-sm min-w-[500px]">
          <thead className="bg-gray-800 text-gray-300 text-xs uppercase tracking-wider">
            <tr>
              <th className="px-4 py-3 text-left">#</th>
              <th className="px-4 py-3 text-left">Team</th>
              <th className="px-4 py-3 text-right">Top 10 Earnings</th>
              <th className="px-4 py-3 text-right">Weekly +/-</th>
              <th className="px-4 py-3 text-left">Golfers in Top 10</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {teams.map((team, i) => (
              <tr key={team.teamId} className={`hover:bg-gray-800/60 ${rowTint(team.weeklyTotal)}`}>
                <td className="px-4 py-3 text-gray-500">{i + 1}</td>
                <td className="px-4 py-3 font-medium text-gray-100 uppercase">{team.teamName}</td>
                <td className="px-4 py-3 text-right tabular-nums text-gray-300">
                  {formatMoney(team.topTenEarnings)}
                </td>
                <td
                  className={`px-4 py-3 text-right tabular-nums font-semibold ${signColor(team.weeklyTotal)}`}
                >
                  {formatSigned(team.weeklyTotal)}
                </td>
                <td className="px-4 py-3">
                  {team.golferScores.length === 0 ? (
                    <span className="text-gray-600">—</span>
                  ) : (
                    team.golferScores.map((g) => {
                      const golferId = g.golferId;
                      return (
                      <span key={`${team.teamId}-${golferId ?? g.golferName}`} className="inline-block mr-3 text-xs">
                        {onGolferClick && golferId ? (
                          <button
                            type="button"
                            onClick={() => onGolferClick(golferId)}
                            className="text-gray-300 hover:underline cursor-pointer bg-transparent border-0 p-0"
                          >
                            {g.golferName}
                          </button>
                        ) : (
                          <span className="text-gray-300">{g.golferName}</span>
                        )}{' '}
                        {g.tied ? <span className="text-gray-500">T</span> : null}
                        <span className="text-gray-400">{g.position ?? '?'}</span>{' '}
                        <span className="text-green-400 tabular-nums">{formatMoney(g.payout)}</span>
                        {g.ownershipPct < 100 ? (
                          <span className="text-gray-500"> ({g.ownershipPct}%)</span>
                        ) : null}
                      </span>
                      );
                    })
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Won</span>
          <span className="font-semibold text-green-400 tabular-nums">
            {formatMoney(weeklyTotals.won)}
          </span>
        </div>
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Lost</span>
          <span className="font-semibold text-red-400 tabular-nums">
            {formatMoney(weeklyTotals.lost)}
          </span>
        </div>
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Net</span>
          <span className={`font-semibold tabular-nums ${signColor(weeklyTotals.net)}`}>
            {formatMoney(weeklyTotals.net)}
          </span>
        </div>
      </div>

      {leaderboard.length > 0 ? (
        <div>
          <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
            Leaderboard
          </h3>
          <div className="overflow-x-auto -mx-3 sm:mx-0">
            <table className="w-full text-sm min-w-[640px]">
              <thead className="bg-gray-800 text-gray-300 text-xs uppercase tracking-wider">
                <tr>
                  <th className="px-3 py-2 text-left">Pos</th>
                  <th className="px-3 py-2 text-left">Player</th>
                  <th className="px-3 py-2 text-left">
                    <ColumnHeaderFilter
                      ariaLabel="Filter leaderboard by team"
                      value={leaderboardTeamFilter}
                      onChange={setLeaderboardTeamFilter}
                      options={leaderboardTeamOptions}
                    />
                  </th>
                  <th className="px-3 py-2 text-right">Score</th>
                  <th className="px-3 py-2 text-right">R1</th>
                  <th className="px-3 py-2 text-right">R2</th>
                  <th className="px-3 py-2 text-right">R3</th>
                  <th className="px-3 py-2 text-right">R4</th>
                  <th className="px-3 py-2 text-right">Tot</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800">
                {filteredLeaderboard.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="px-3 py-6 text-center text-gray-500 italic">
                      No players match this filter.
                    </td>
                  </tr>
                ) : (
                  filteredLeaderboard.map((entry) => (
                    <tr
                      key={playerNames(entry)}
                      className={entry.rostered ? 'bg-green-900/10' : ''}
                    >
                      <td className="px-3 py-2 text-gray-400">{entry.position}</td>
                      <td className="px-3 py-2 text-gray-100">{playerNames(entry)}</td>
                      <td
                        className={`px-3 py-2 text-xs ${entry.rostered ? 'text-green-400 uppercase' : 'text-gray-600'}`}
                      >
                        {teamLabel(entry)}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">
                        {formatScoreToPar(entry.scoreToPar)}
                      </td>
                      {[0, 1, 2, 3].map((idx) => (
                        <td key={idx} className="px-3 py-2 text-right tabular-nums text-gray-400">
                          {entry.roundScores[idx] ?? '—'}
                        </td>
                      ))}
                      <td className="px-3 py-2 text-right tabular-nums font-semibold">
                        {entry.totalStrokes ?? '—'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </section>
  );
}

export default ScoreboardView;
