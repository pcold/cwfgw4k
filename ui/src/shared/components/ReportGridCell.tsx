import type { ReactElement, ReactNode } from 'react';

/** @see ReportGridCell */
export type ReportGridCellFill = 'plain' | 'late-row-leader';

const CELL_CLASS = 'px-2 py-1.5 align-top';

// Emphasis, not the only cue: the fill is 1.07:1 on white and its gold edge 1.44:1, short of a lone cue's 3:1.
// Late Row Bets prints each round's payouts in text.
const FILL_CLASS: Readonly<Record<ReportGridCellFill, string>> = {
  plain: 'bg-surface-card',
  'late-row-leader': 'bg-late-row-leader border-l-4 border-gold',
};

interface ReportGridCellProps {
  readonly fill?: ReportGridCellFill;
  readonly children: ReactNode;
}

/**
 * No border: the table's border-spacing shows the canvas between cells. No `className`, so a caller can't add a
 * second fill; its alignment goes on the children.
 */
export default function ReportGridCell({ fill = 'plain', children }: ReportGridCellProps): ReactElement {
  return <td className={`${CELL_CLASS} ${FILL_CLASS[fill]}`}>{children}</td>;
}
