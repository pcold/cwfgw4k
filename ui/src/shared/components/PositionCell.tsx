import type { ReactElement } from 'react';

type PositionTone = 'leader' | 'field';

/** `row-start` is a table card row's first cell, a `td`; `card-start` is a phone card's first box, a `div`. */
export type PositionCellShape = 'row-start' | 'card-start';

/** A `round` box numbers a draft round, so its 1 is never gold: gold marks first place. */
export type PositionCellKind = 'position' | 'round';

const FIGURE_CLASS = 'font-display font-bold text-lg tabular-nums';

const SHAPE_CLASS: Readonly<Record<PositionCellShape, string>> = {
  'row-start': 'px-3 py-2 rounded-l text-center',
  'card-start': 'w-9.5 shrink-0 flex items-center justify-center rounded-l',
};

const TONE_CLASS: Readonly<Record<PositionTone, string>> = {
  leader: 'bg-gold text-on-gold',
  field: 'bg-position-fill text-on-fill',
};

interface PositionCellProps {
  readonly position: number;
  readonly shape?: PositionCellShape;
  readonly kind?: PositionCellKind;
}

/**
 * Rounds the card's left corners, so it is the row's first box. No `className`, so a caller can't fight the
 * fill. Gold marks a `position` box numbered 1, not a tie for first: the number is the row's order.
 */
export default function PositionCell({
  position,
  shape = 'row-start',
  kind = 'position',
}: PositionCellProps): ReactElement {
  const tone: PositionTone = kind === 'position' && position === 1 ? 'leader' : 'field';
  const className = `${SHAPE_CLASS[shape]} ${FIGURE_CLASS} ${TONE_CLASS[tone]}`;
  switch (shape) {
    case 'row-start':
      return <td className={className}>{position}</td>;
    case 'card-start':
      return <div className={className}>{position}</div>;
    default:
      return shape satisfies never;
  }
}
