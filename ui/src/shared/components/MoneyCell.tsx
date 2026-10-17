import type { ReactElement } from 'react';
import type { MoneyTone } from '@/shared/util/money';
import { moneyTone } from '@/shared/util/money';

/**
 * White on the up fill is 4.40:1, which passes only as large text: bold and at least 18.66px. The contrast
 * test in `index.css.test.ts` reads this class to hold it there.
 */
export const MONEY_FIGURE_CLASS = 'font-display font-bold text-xl tabular-nums';

const CELL_CLASS = `px-3 py-2 rounded-r text-right text-on-fill ${MONEY_FIGURE_CLASS}`;

const TONE_CLASS: Readonly<Record<MoneyTone, string>> = {
  up: 'bg-money-up-fill',
  down: 'bg-money-down-fill',
  even: 'bg-money-even-fill',
};

interface MoneyCellProps {
  readonly display: string;
}

/**
 * Rounds the card's right corners, so it is the row's last cell. No `className`, so a caller can't fight the
 * fill. Takes the formatted text, as `moneyTone` does, so the box tones at the precision it prints.
 */
export default function MoneyCell({ display }: MoneyCellProps): ReactElement {
  return <td className={`${CELL_CLASS} ${TONE_CLASS[moneyTone(display)]}`}>{display}</td>;
}
