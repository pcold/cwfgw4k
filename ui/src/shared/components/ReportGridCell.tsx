import type { ReactElement, ReactNode } from 'react';

/** @see ReportGridCell */
export type ReportGridCellFill = 'plain' | 'late-row-leader';

const CELL_CLASS = 'px-2 py-1.5 align-top';

// The gold edge is decorative: the leader's fill alone carries the state, so the edge needs no contrast floor.
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
