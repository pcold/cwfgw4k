import type { ReactElement } from 'react';
import { moneyAmount, moneyTone, type MoneyTone } from '@/shared/util/money';
import { PULSE_CLASS, usePulse } from './Pulse';

/**
 * White on the up fill is 4.40:1, which passes only as large text: bold and at least 18.66px. The contrast
 * test in `index.css.test.ts` reads this class to hold it there.
 */
export const MONEY_FIGURE_CLASS = 'font-display font-bold text-xl tabular-nums';

/**
 * `row-end` rounds a card row's right corners, so it is that row's last cell; `standalone` is a square grid cell;
 * `card-end` is a phone card's last box, a `div` rather than a `td`.
 */
export type MoneyCellShape = 'row-end' | 'standalone' | 'card-end';

const SHAPE_CLASS: Readonly<Record<MoneyCellShape, string>> = {
  'row-end': 'px-3 py-2 rounded-r',
  standalone: 'px-3 py-2',
  'card-end': 'min-w-24 px-3 shrink-0 flex items-center justify-center rounded-r',
};

const TONE_CLASS: Readonly<Record<MoneyTone, string>> = {
  up: 'bg-money-up-fill',
  down: 'bg-money-down-fill',
  even: 'bg-money-even-fill',
};

interface MoneyCellProps {
  readonly display: string;
  readonly shape?: MoneyCellShape;
  /** @see usePulse */
  readonly pulseScope?: string;
}

/**
 * No `className`, so a caller can't fight the fill. Takes the formatted text, as `moneyTone` does, so the box
 * tones at the precision it prints.
 */
export default function MoneyCell({ display, shape = 'row-end', pulseScope }: MoneyCellProps): ReactElement {
  const className = `${SHAPE_CLASS[shape]} text-right text-on-fill ${MONEY_FIGURE_CLASS} ${TONE_CLASS[moneyTone(display)]} ${PULSE_CLASS}`;
  const pulse = usePulse(display, moneyAmount(display), 'higher', pulseScope);
  switch (shape) {
    case 'row-end':
    case 'standalone':
      return (
        <td className={className} {...pulse}>
          {display}
        </td>
      );
    case 'card-end':
      return (
        <div className={className} {...pulse}>
          {display}
        </div>
      );
    default:
      return shape satisfies never;
  }
}
