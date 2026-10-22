import type { ReactNode } from 'react';
import MoneyCell from '@/shared/components/MoneyCell';
import { formatWholeDollars } from '@/shared/util/money';
import { standingStats, type YourTeam } from './yourTeamCardModel';

interface Props {
  yourTeam: YourTeam;
  teamCount: number;
  /** @see LiveOverlayQuery.selectionKey */
  selectionKey: string;
}

const LABEL_CLASS = 'font-display text-xs font-semibold uppercase tracking-widest text-on-chrome-muted';

/** Name and total box share the top row on a phone, the stats wrapping below; from `sm` the box ends one row. */
function YourTeamCard({ yourTeam: { team, standing }, teamCount, selectionKey }: Props): ReactNode {
  // A new team of the visitor's swaps the total in place, which is not a refresh.
  const pulseScope = `${selectionKey}|${team.teamId}`;
  return (
    <section aria-label="Your team" className="flex flex-wrap sm:flex-nowrap rounded-lg overflow-hidden bg-chrome">
      <div className="flex-1 min-w-0 px-4 py-3">
        <p className={LABEL_CLASS}>Your team</p>
        <p className="font-display text-3xl font-bold italic uppercase truncate text-on-chrome">{team.teamName}</p>
      </div>
      <div className="flex sm:order-last">
        <MoneyCell display={formatWholeDollars(team.totalCash)} shape="card-end" pulseScope={pulseScope} />
      </div>
      <dl className="basis-full grid grid-flow-col auto-cols-fr sm:basis-auto sm:flex-[2]">
        {standingStats(standing, teamCount).map((stat) => (
          <div key={stat.label} className="px-4 py-3">
            <dt className={LABEL_CLASS}>{stat.label}</dt>
            <dd className="font-display text-xl font-bold tabular-nums text-on-chrome">{stat.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export default YourTeamCard;
