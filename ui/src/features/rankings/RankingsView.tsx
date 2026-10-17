import type { ReactNode } from 'react';
import type { Rankings } from '@/shared/api/types';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import MoneyCell from '@/shared/components/MoneyCell';
import PositionCell from '@/shared/components/PositionCell';
import { formatDollarsAndCents, formatWholeDollars, moneyToneClass } from '@/shared/util/money';
import RankingsChart from './RankingsChart';

const CARD_CELL_CLASS = 'px-3 py-2 bg-surface-card';

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
        <table className="min-w-full text-sm border-separate border-spacing-y-1">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr className="text-left">
              <th className="px-3 py-2 w-14 text-center">#</th>
              <th className="px-3 py-2">Team</th>
              <th className="px-3 py-2 text-right">Subtotal</th>
              <th className="px-3 py-2 text-right">Late Row Bets</th>
              <th className="px-3 py-2 w-36 text-right">Total Cash</th>
            </tr>
          </thead>
          <tbody>
            {rankings.teams.map((team, index) => (
              <tr key={team.teamId} className="h-11">
                <PositionCell position={index + 1} />
                <td className={`${CARD_CELL_CLASS} font-display font-bold text-lg uppercase text-ink`}>
                  {team.teamName}
                </td>
                <td className={`${CARD_CELL_CLASS} text-right tabular-nums`}>{formatDollarsAndCents(team.subtotal)}</td>
                <td className={`${CARD_CELL_CLASS} text-right tabular-nums`}>{formatDollarsAndCents(team.sideBets)}</td>
                <MoneyCell display={formatWholeDollars(team.totalCash)} />
              </tr>
            ))}
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
