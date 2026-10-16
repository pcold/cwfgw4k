import type { ReactNode } from 'react';
import type { Rankings, TeamRanking } from '@/shared/api/types';
import { formatDollarsAndCents, formatWholeDollars } from '@/shared/util/money';
import RankingsChart from './RankingsChart';

function signColor(value: number): string {
  if (value > 0) return 'text-semantic-success';
  if (value < 0) return 'text-semantic-danger';
  return 'text-ink-muted';
}

interface Props {
  rankings: Rankings;
}

function sumWhere(teams: TeamRanking[], predicate: (t: TeamRanking) => boolean): number {
  return teams.filter(predicate).reduce((acc, t) => acc + t.totalCash, 0);
}

function RankingsView({ rankings }: Props): ReactNode {
  const sorted = [...rankings.teams].sort((a, b) => b.totalCash - a.totalCash);
  const sortedRankings: Rankings = { ...rankings, teams: sorted };
  const totalWon = sumWhere(sorted, (t) => t.totalCash > 0);
  const totalLost = sumWhere(sorted, (t) => t.totalCash < 0);
  const net = totalWon + totalLost;

  return (
    <section className="space-y-6">
      <header className="border-b border-hairline pb-3">
        <h2 className="text-xl font-semibold text-primary-ink">Team Standings</h2>
        <p className="text-xs text-ink-muted">
          {rankings.weeks.length} weeks played
          {rankings.live ? ' · live overlay on' : ''}
        </p>
      </header>

      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-ink-soft">
              <th className="px-3 py-2 w-12">#</th>
              <th className="px-3 py-2">Team</th>
              <th className="px-3 py-2 text-right">Subtotal</th>
              <th className="px-3 py-2 text-right">Late Row Bets</th>
              <th className="px-3 py-2 text-right">Total Cash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline-soft">
            {sorted.map((team, index) => (
              <tr key={team.teamId} className="hover:bg-surface-card/60">
                <td className="px-3 py-2 text-ink-soft">{index + 1}</td>
                <td className="px-3 py-2 font-medium text-ink uppercase">{team.teamName}</td>
                <td
                  className={`px-3 py-2 text-right tabular-nums ${signColor(team.subtotal)}`}
                >
                  {formatDollarsAndCents(team.subtotal)}
                </td>
                <td
                  className={`px-3 py-2 text-right tabular-nums ${signColor(team.sideBets)}`}
                >
                  {formatDollarsAndCents(team.sideBets)}
                </td>
                <td
                  className={`px-3 py-2 text-right tabular-nums font-semibold ${signColor(team.totalCash)}`}
                >
                  {formatWholeDollars(team.totalCash)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Won</span>
          <span className="font-semibold text-semantic-success tabular-nums">
            {formatWholeDollars(totalWon)}
          </span>
        </div>
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Lost</span>
          <span className="font-semibold text-semantic-danger tabular-nums">
            {formatWholeDollars(totalLost)}
          </span>
        </div>
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Net</span>
          <span className={`font-semibold tabular-nums ${signColor(net)}`}>
            {formatWholeDollars(net)}
          </span>
        </div>
      </div>

      <RankingsChart rankings={sortedRankings} />
    </section>
  );
}

export default RankingsView;
