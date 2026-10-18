import type { ReactElement, ReactNode } from 'react';

interface ReportGridRoundLabelProps {
  readonly round: number;
  readonly lateRow: boolean;
  readonly children?: ReactNode;
}

/**
 * Filled with the canvas, not left clear, so the team cells scroll under the sticky column rather than show
 * through. `children` goes under the tag: the report's per-round stake.
 */
export default function ReportGridRoundLabel({ round, lateRow, children }: ReportGridRoundLabelProps): ReactElement {
  return (
    <td className="px-1 py-1 text-center align-middle sticky left-0 z-10 bg-canvas">
      <div className="font-display font-bold text-xl text-ink">{round}</div>
      {lateRow ? (
        <div className="font-display font-bold text-xs uppercase tracking-wider text-ink-muted">Late row</div>
      ) : null}
      {children}
    </td>
  );
}
