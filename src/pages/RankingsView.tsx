import type { Rankings, TeamRanking } from '../api/types';

const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

function formatMoney(value: number): string {
  return money.format(value);
}

function signColor(value: number): string {
  if (value > 0) return 'text-green-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-400';
}

interface Props {
  rankings: Rankings;
}

function sumWhere(teams: TeamRanking[], predicate: (t: TeamRanking) => boolean): number {
  return teams.filter(predicate).reduce((acc, t) => acc + t.totalCash, 0);
}

function RankingsView({ rankings }: Props) {
  const sorted = [...rankings.teams].sort((a, b) => b.totalCash - a.totalCash);
  const totalWon = sumWhere(sorted, (t) => t.totalCash > 0);
  const totalLost = sumWhere(sorted, (t) => t.totalCash < 0);
  const net = totalWon + totalLost;

  return (
    <section className="space-y-6">
      <header className="border-b border-gray-700 pb-3">
        <h2 className="text-xl font-semibold text-green-400">Team Standings</h2>
        <p className="text-xs text-gray-400">
          {rankings.weeks.length} weeks played
          {rankings.live ? ' · live overlay on' : ''}
        </p>
      </header>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
              <th className="px-3 py-2 w-12">#</th>
              <th className="px-3 py-2">Team</th>
              <th className="px-3 py-2 text-right">Subtotal</th>
              <th className="px-3 py-2 text-right">Late Row Bets</th>
              <th className="px-3 py-2 text-right">Total Cash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {sorted.map((team, index) => (
              <tr key={team.teamId} className="hover:bg-gray-800/60">
                <td className="px-3 py-2 text-gray-500">{index + 1}</td>
                <td className="px-3 py-2 font-medium text-gray-100">{team.teamName}</td>
                <td
                  className={`px-3 py-2 text-right tabular-nums ${signColor(team.subtotal)}`}
                >
                  {formatMoney(team.subtotal)}
                </td>
                <td
                  className={`px-3 py-2 text-right tabular-nums ${signColor(team.sideBets)}`}
                >
                  {formatMoney(team.sideBets)}
                </td>
                <td
                  className={`px-3 py-2 text-right tabular-nums font-semibold ${signColor(team.totalCash)}`}
                >
                  {formatMoney(team.totalCash)}
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
            {formatMoney(totalWon)}
          </span>
        </div>
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Lost</span>
          <span className="font-semibold text-red-400 tabular-nums">{formatMoney(totalLost)}</span>
        </div>
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Net</span>
          <span className={`font-semibold tabular-nums ${signColor(net)}`}>{formatMoney(net)}</span>
        </div>
      </div>
    </section>
  );
}

export default RankingsView;
