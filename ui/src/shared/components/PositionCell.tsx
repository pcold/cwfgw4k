import type { ReactElement } from 'react';

type PositionTone = 'leader' | 'field';

const CELL_CLASS = 'px-3 py-2 rounded-l text-center font-display font-bold text-lg tabular-nums';

const TONE_CLASS: Readonly<Record<PositionTone, string>> = {
  leader: 'bg-gold text-on-gold',
  field: 'bg-position-fill text-on-fill',
};

interface PositionCellProps {
  readonly position: number;
}

/**
 * Rounds the card's left corners, so it is the row's first cell. No `className`, so a caller can't fight the
 * fill. Gold marks the row numbered 1, not a tie for first: the number is the row's order.
 */
export default function PositionCell({ position }: PositionCellProps): ReactElement {
  const tone: PositionTone = position === 1 ? 'leader' : 'field';
  return <td className={`${CELL_CLASS} ${TONE_CLASS[tone]}`}>{position}</td>;
}
