import type { WeeklyReport } from '../api/types';

const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

function formatMoney(value: number): string {
  return money.format(value);
}

function formatSigned(value: number): string {
  if (value === 0) return '$0';
  const sign = value > 0 ? '+' : '-';
  return `${sign}${money.format(Math.abs(value))}`;
}

interface Props {
  report: WeeklyReport;
}

function WeeklyReportView({ report }: Props) {
  const { tournament, teams, standingsOrder } = report;

  return (
    <section className="space-y-6">
      <header className="border-b border-gray-700 pb-3">
        <h2 className="text-xl font-semibold text-green-400">
          {tournament.name ?? 'Season Report'}
        </h2>
        <p className="text-xs text-gray-400">
          {tournament.startDate} – {tournament.endDate}
          {tournament.payoutMultiplier && tournament.payoutMultiplier > 1
            ? ` · ${tournament.payoutMultiplier}x payouts`
            : ''}
          {tournament.status ? ` · ${tournament.status}` : ''}
        </p>
      </header>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
              <th className="px-3 py-2">Team</th>
              <th className="px-3 py-2 text-right">Weekly</th>
              <th className="px-3 py-2 text-right">Previous</th>
              <th className="px-3 py-2 text-right">Subtotal</th>
              <th className="px-3 py-2 text-right">Side Bets</th>
              <th className="px-3 py-2 text-right">Total Cash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {teams.map((team) => (
              <tr key={team.teamId} className="hover:bg-gray-800/60">
                <td className="px-3 py-2">
                  <div className="font-medium text-gray-100">{team.teamName}</div>
                  <div className="text-xs text-gray-500">{team.ownerName}</div>
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatSigned(team.weeklyTotal)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-gray-400">
                  {formatMoney(team.previous)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatMoney(team.subtotal)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-gray-400">
                  {formatSigned(team.sideBets)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums font-semibold text-green-400">
                  {formatMoney(team.totalCash)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {standingsOrder.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-300 mb-2">Standings</h3>
          <ol className="space-y-1 text-sm">
            {standingsOrder.map((entry) => (
              <li
                key={`${entry.rank}-${entry.teamName}`}
                className="flex justify-between tabular-nums"
              >
                <span className="text-gray-300">
                  <span className="text-gray-500 w-6 inline-block">{entry.rank}.</span>
                  {entry.teamName}
                </span>
                <span className="text-green-400 font-medium">{formatMoney(entry.totalCash)}</span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  );
}

export default WeeklyReportView;
