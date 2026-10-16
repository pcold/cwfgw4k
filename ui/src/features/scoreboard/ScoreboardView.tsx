import { useMemo, useState, type ReactNode } from 'react';
import type { WeeklyReport } from '@/shared/api/types';
import { formatDollarsAndCents, moneyToneClass } from '@/shared/util/money';
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
  const won = formatDollarsAndCents(weeklyTotals.won);
  const lost = formatDollarsAndCents(weeklyTotals.lost);
  const net = formatDollarsAndCents(weeklyTotals.net);
  const statusBanner = isCompleted
    ? 'Results finalized'
    : 'Tournament in progress — scores are live estimates';
  const bannerClass = isCompleted
    ? 'bg-semantic-success-tint/30 border-semantic-success-soft text-semantic-success-ink'
    : 'bg-semantic-warning-tint/40 border-semantic-warning-soft text-semantic-warning-ink';

  return (
    <section className="space-y-6">
      <header className="border-b border-hairline pb-3">
        <h2 className="text-xl font-semibold text-primary-ink">
          {tournament.name ?? 'Scoreboard'}
        </h2>
        <p className="text-xs text-ink-muted">
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
          <thead className="bg-surface-card text-ink-label text-xs uppercase tracking-wider">
            <tr>
              <th className="px-4 py-3 text-left">#</th>
              <th className="px-4 py-3 text-left">Team</th>
              <th className="px-4 py-3 text-right">Top 10 Earnings</th>
              <th className="px-4 py-3 text-right">Weekly +/-</th>
              <th className="px-4 py-3 text-left">Golfers in Top 10</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline-soft">
            {teams.map((team, i) => {
              const weeklyTotal = formatDollarsAndCents(team.weeklyTotal);
              return (
                <tr key={team.teamId} className="hover:bg-surface-card/60">
                  <td className="px-4 py-3 text-ink-soft tabular-nums">{i + 1}</td>
                  <td className="px-4 py-3 font-medium text-ink uppercase">{team.teamName}</td>
                  <td className="px-4 py-3 text-right tabular-nums text-ink-secondary">
                    {formatDollarsAndCents(team.topTenEarnings)}
                  </td>
                  <td
                    className={`px-4 py-3 text-right tabular-nums font-semibold ${moneyToneClass(weeklyTotal)}`}
                  >
                    {weeklyTotal}
                  </td>
                  <td className="px-4 py-3">
                    {team.golferScores.length === 0 ? (
                      <span className="text-ink-faint">—</span>
                    ) : (
                      team.golferScores.map((g) => {
                        const golferId = g.golferId;
                        return (
                        <span key={`${team.teamId}-${golferId ?? g.golferName}`} className="inline-block mr-3 text-xs">
                          {onGolferClick && golferId ? (
                            <button
                              type="button"
                              onClick={() => onGolferClick(golferId)}
                              className="text-ink-secondary hover:underline cursor-pointer bg-transparent border-0 p-0"
                            >
                              {g.golferName}
                            </button>
                          ) : (
                            <span className="text-ink-secondary">{g.golferName}</span>
                          )}{' '}
                          {g.tied ? <span className="text-ink-soft">T</span> : null}
                          <span className="text-ink-muted">{g.position ?? '?'}</span>{' '}
                          <span className="tabular-nums">{formatDollarsAndCents(g.payout)}</span>
                          {g.ownershipPct < 100 ? (
                            <span className="text-ink-soft"> ({g.ownershipPct}%)</span>
                          ) : null}
                        </span>
                        );
                      })
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Won</span>
          <span className={`font-semibold tabular-nums ${moneyToneClass(won)}`}>
            {won}
          </span>
        </div>
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Lost</span>
          <span className={`font-semibold tabular-nums ${moneyToneClass(lost)}`}>
            {lost}
          </span>
        </div>
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Net</span>
          <span className={`font-semibold tabular-nums ${moneyToneClass(net)}`}>
            {net}
          </span>
        </div>
      </div>

      {leaderboard.length > 0 ? (
        <div>
          <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider mb-2">
            Leaderboard
          </h3>
          <div className="overflow-x-auto -mx-3 sm:mx-0">
            <table className="w-full text-sm min-w-[640px]">
              <thead className="bg-surface-card text-ink-label text-xs uppercase tracking-wider">
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
              <tbody className="divide-y divide-hairline-soft">
                {filteredLeaderboard.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="px-3 py-6 text-center text-ink-soft italic">
                      No players match this filter.
                    </td>
                  </tr>
                ) : (
                  filteredLeaderboard.map((entry) => (
                    <tr
                      key={playerNames(entry)}
                      className={entry.rostered ? 'bg-semantic-success-tint/10' : ''}
                    >
                      <td className="px-3 py-2 text-ink-muted tabular-nums">{entry.position}</td>
                      <td className="px-3 py-2 text-ink">{playerNames(entry)}</td>
                      <td
                        className={`px-3 py-2 text-xs ${entry.rostered ? 'text-semantic-success uppercase' : 'text-ink-faint'}`}
                      >
                        {teamLabel(entry)}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">
                        {formatScoreToPar(entry.scoreToPar)}
                      </td>
                      {[0, 1, 2, 3].map((idx) => (
                        <td key={idx} className="px-3 py-2 text-right tabular-nums text-ink-muted">
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
