import type { ReactElement, ReactNode } from 'react';
import { m } from 'motion/react';
import { TAP_TARGET_CLASS } from './formControls';
import { glideAt } from './glide';
import PositionCell, { type PositionCellKind } from './PositionCell';
import YouTag from './YouTag';

/** @see ListCard */
export const CARD_LIST_CLASS = 'flex flex-col gap-1';

/** For a golfer's name that opens their history; `max-w-full` keeps the card's truncation working inside it. */
export const CARD_NAME_BUTTON_CLASS = `max-w-full truncate text-left hover:text-primary-ink hover:underline ${TAP_TARGET_CLASS}`;

/** Uppercase is structure for a team's name; a golfer's prints as the server spells it. */
export type ListCardNameCase = 'team' | 'golfer';

const NAME_CASE_CLASS: Readonly<Record<ListCardNameCase, string>> = {
  team: 'uppercase',
  golfer: 'normal-case',
};

/** `my-team` also sets a `YouTag` after the name, so the visitor's team never rests on the fill alone. */
export type ListCardFill = 'plain' | 'late-row-leader' | 'my-team';

const MIDDLE_CLASS = 'flex-1 min-w-0 px-3 py-2 flex flex-col justify-center';

// A late-row leader's gold edge sits on the middle, beside the box, as the grid cell's does; my team's is a ring
// on the card. Either way the fill alone is 1.07:1 on white.
const FILL_CLASS: Readonly<Record<ListCardFill, { card: string; middle: string }>> = {
  plain: { card: 'bg-surface-card', middle: MIDDLE_CLASS },
  'late-row-leader': { card: 'bg-late-row-leader', middle: `${MIDDLE_CLASS} border-l-4 border-gold` },
  'my-team': { card: 'bg-my-team ring-2 ring-gold', middle: MIDDLE_CLASS },
};

/**
 * A table card row's look; `my-team` is `ListCard`'s, its ring the `tr`'s outline. Inset, since the table's
 * `overflow-x-auto` wrapper clips the sides of an outline drawn outside the row.
 */
export type CardRowFill = Extract<ListCardFill, 'plain' | 'my-team'>;

/** @see CardRowFill; `cell` fills each cell after the row's position box but its money box, if any. */
export const CARD_ROW_CLASS: Readonly<Record<CardRowFill, { row: string; cell: string }>> = {
  plain: { row: 'h-11', cell: 'bg-surface-card' },
  'my-team': { row: 'h-11 rounded outline-2 -outline-offset-2 outline-gold', cell: 'bg-my-team' },
};

interface ListCardProps {
  readonly position: number;
  readonly positionKind?: PositionCellKind;
  readonly name: ReactNode;
  readonly nameCase: ListCardNameCase;
  readonly detail: ReactNode;
  /** A `MoneyCell` with `shape="card-end"`, a plain figure where the total takes no fill, or a pick's tag. */
  readonly total: ReactNode;
  readonly fill?: ListCardFill;
  /**
   * Unset, the card never glides. Key the list by its selection, so a new one remounts the cards rather than
   * shuffling them.
   * @see glideAt
   */
  readonly glideIndex?: number;
}

/**
 * Below `sm` only; the list's table takes over from there. `min-w-0` lets the middle shrink, so a long name
 * truncates rather than push the total off the screen.
 */
export default function ListCard({
  position,
  positionKind = 'position',
  name,
  nameCase,
  detail,
  total,
  fill = 'plain',
  glideIndex,
}: ListCardProps): ReactElement {
  const nameLine = (
    <div className={`font-display font-bold text-lg text-ink truncate min-w-0 ${NAME_CASE_CLASS[nameCase]}`}>
      {name}
    </div>
  );
  const className = `flex min-h-13.5 rounded ${FILL_CLASS[fill].card}`;
  const content = (
    <>
      <PositionCell position={position} shape="card-start" kind={positionKind} />
      <div className={FILL_CLASS[fill].middle}>
        {fill === 'my-team' ? (
          <div className="flex items-center">
            {nameLine}
            <YouTag />
          </div>
        ) : (
          nameLine
        )}
        <div className="text-sm text-ink-muted">{detail}</div>
      </div>
      {total}
    </>
  );
  return glideIndex === undefined ? (
    <li className={className}>{content}</li>
  ) : (
    <m.li {...glideAt(glideIndex)} className={className}>
      {content}
    </m.li>
  );
}
