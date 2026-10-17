import type { ReactNode } from 'react';
import type { Rankings } from '@/shared/api/types';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import { formatDollarsAndCents, formatWholeDollars, moneyToneClass } from '@/shared/util/money';
import RankingsChart from './RankingsChart';

interface Props {
  rankings: Rankings;
}

function RankingsView({ rankings }: Props): ReactNode {
  const wonText = formatWholeDollars(rankings.cashTotals.won);
  const lostText = formatWholeDollars(rankings.cashTotals.lost);
  const netText = formatWholeDollars(rankings.cashTotals.net);

  return (
    <section className="space-y-6">
      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="min-w-full text-sm">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr className="text-left">
              <th className="px-3 py-2 w-12">#</th>
              <th className="px-3 py-2">Team</th>
              <th className="px-3 py-2 text-right">Subtotal</th>
              <th className="px-3 py-2 text-right">Late Row Bets</th>
              <th className="px-3 py-2 text-right">Total Cash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline-soft">
            {rankings.teams.map((team, index) => {
              const totalCash = formatWholeDollars(team.totalCash);
              return (
                <tr key={team.teamId} className="hover:bg-surface-card/60">
                  <td className="px-3 py-2 text-ink-soft tabular-nums">{index + 1}</td>
                  <td className="px-3 py-2 font-medium text-ink uppercase">{team.teamName}</td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {formatDollarsAndCents(team.subtotal)}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {formatDollarsAndCents(team.sideBets)}
                  </td>
                  <td
                    className={`px-3 py-2 text-right tabular-nums font-semibold ${moneyToneClass(totalCash)}`}
                  >
                    {totalCash}
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
          <span className={`font-semibold tabular-nums ${moneyToneClass(wonText)}`}>
            {wonText}
          </span>
        </div>
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Lost</span>
          <span className={`font-semibold tabular-nums ${moneyToneClass(lostText)}`}>
            {lostText}
          </span>
        </div>
        <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
          <span className="text-ink-soft">Net</span>
          <span className={`font-semibold tabular-nums ${moneyToneClass(netText)}`}>
            {netText}
          </span>
        </div>
      </div>

      <RankingsChart rankings={rankings} />
    </section>
  );
}

export default RankingsView;
