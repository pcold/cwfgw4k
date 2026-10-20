import type { ReactElement, ReactNode } from 'react';
import PositionCell from './PositionCell';

/** @see ListCard */
export const CARD_LIST_CLASS = 'flex flex-col gap-1';

/** Uppercase is structure for a team's name; a golfer's prints as the server spells it. */
export type ListCardNameCase = 'team' | 'golfer';

const NAME_CASE_CLASS: Readonly<Record<ListCardNameCase, string>> = {
  team: 'uppercase',
  golfer: 'normal-case',
};

interface ListCardProps {
  readonly position: number;
  readonly name: ReactNode;
  readonly nameCase: ListCardNameCase;
  readonly detail: ReactNode;
  /** A `MoneyCell` with `shape="card-end"`, or a plain figure where the total takes no fill. */
  readonly total: ReactNode;
}

/**
 * Below `sm` only; the list's table takes over from there. `min-w-0` lets the middle shrink, so a long name
 * truncates rather than push the total off the screen.
 */
export default function ListCard({ position, name, nameCase, detail, total }: ListCardProps): ReactElement {
  return (
    <li className="flex min-h-13.5 rounded bg-surface-card">
      <PositionCell position={position} shape="card-start" />
      <div className="flex-1 min-w-0 px-3 py-2 flex flex-col justify-center">
        <div className={`font-display font-bold text-lg text-ink truncate ${NAME_CASE_CLASS[nameCase]}`}>{name}</div>
        <div className="text-sm text-ink-muted">{detail}</div>
      </div>
      {total}
    </li>
  );
}
