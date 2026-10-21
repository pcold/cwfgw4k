import type { ReactNode } from 'react';
import StatCard from './StatCard';

interface WonLostNetCardsProps {
  readonly won: string;
  readonly lost: string;
  readonly net: string;
}

/** @see StatCard */
function WonLostNetCards({ won, lost, net }: WonLostNetCardsProps): ReactNode {
  return (
    <div className="flex flex-wrap gap-3 text-sm">
      <StatCard label="Won" amount={won} />
      <StatCard label="Lost" amount={lost} />
      <StatCard label="Net" amount={net} />
    </div>
  );
}

export default WonLostNetCards;
