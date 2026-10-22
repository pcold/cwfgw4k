import type { ReactNode } from 'react';
import { m } from 'motion/react';
import type { Rankings } from '@/shared/api/types';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import { glideAt } from '@/shared/components/glide';
import ListCard, { CARD_LIST_CLASS, CARD_ROW_CLASS } from '@/shared/components/ListCard';
import MoneyCell from '@/shared/components/MoneyCell';
import PositionCell from '@/shared/components/PositionCell';
import WonLostNetCards from '@/shared/components/WonLostNetCards';
import YouTag from '@/shared/components/YouTag';
import { isMyTeam } from '@/features/myTeam/myTeamModel';
import { formatDollarsAndCents, formatWholeDollars } from '@/shared/util/money';
import RankingsChart from './RankingsChart';
import YourTeamCard from './YourTeamCard';
import { findYourTeam } from './yourTeamCardModel';

const CARD_CELL_CLASS = 'px-3 py-2';

interface Props {
  rankings: Rankings;
  /** @see isMyTeam */
  myTeamName: string | null;
  /** @see LiveOverlayQuery.selectionKey */
  selectionKey: string;
}

function RankingsView({ rankings, myTeamName, selectionKey }: Props): ReactNode {
  const wonText = formatWholeDollars(rankings.cashTotals.won);
  const lostText = formatWholeDollars(rankings.cashTotals.lost);
  const netText = formatWholeDollars(rankings.cashTotals.net);
  const yourTeam = findYourTeam(rankings, myTeamName);

  return (
    <section className="space-y-6">
      {yourTeam ? <YourTeamCard yourTeam={yourTeam} teamCount={rankings.teams.length} /> : null}
      <ol key={selectionKey} aria-label="Standings" className={`sm:hidden ${CARD_LIST_CLASS}`}>
        {rankings.teams.map((team, index) => (
          <ListCard
            key={team.teamId}
            position={index + 1}
            name={team.teamName}
            nameCase="team"
            fill={isMyTeam(team.teamName, myTeamName) ? 'my-team' : 'plain'}
            detail={`Sub ${formatDollarsAndCents(team.subtotal)} · Bets ${formatDollarsAndCents(team.sideBets)}`}
            total={<MoneyCell display={formatWholeDollars(team.totalCash)} shape="card-end" />}
            glideIndex={index}
          />
        ))}
      </ol>

      <div className="hidden sm:block overflow-x-auto -mx-3 sm:mx-0">
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
          <tbody key={selectionKey}>
            {rankings.teams.map((team, index) => {
              const mine = isMyTeam(team.teamName, myTeamName);
              const look = CARD_ROW_CLASS[mine ? 'my-team' : 'plain'];
              const cellClass = `${CARD_CELL_CLASS} ${look.cell}`;
              return (
                <m.tr key={team.teamId} {...glideAt(index)} className={look.row}>
                  <PositionCell position={index + 1} />
                  <td className={`${cellClass} font-display font-bold text-lg uppercase text-ink`}>
                    {team.teamName}
                    {mine ? <YouTag /> : null}
                  </td>
                  <td className={`${cellClass} text-right tabular-nums`}>{formatDollarsAndCents(team.subtotal)}</td>
                  <td className={`${cellClass} text-right tabular-nums`}>{formatDollarsAndCents(team.sideBets)}</td>
                  <MoneyCell display={formatWholeDollars(team.totalCash)} />
                </m.tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <WonLostNetCards won={wonText} lost={lostText} net={netText} />

      <RankingsChart rankings={rankings} />
    </section>
  );
}

export default RankingsView;
