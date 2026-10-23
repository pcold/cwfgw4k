import type { ReactElement } from 'react';
import { COLUMN_HEADER_CLASS } from './columnHeader';

/** @see ReportGridHead */
export interface ReportGridTeam {
  readonly teamId: string;
  readonly number: number;
  readonly name: string;
}

interface ReportGridHeadProps {
  readonly cornerLabel: string;
  readonly teams: readonly ReportGridTeam[];
}

/**
 * The sticky corner is filled with the canvas, not left clear, so team heads scroll under it rather than show
 * through. A team head keeps the shared head type but for the band's ink, `text-on-fill`, a regular-weight
 * number and a name a size up.
 */
export default function ReportGridHead({ cornerLabel, teams }: ReportGridHeadProps): ReactElement {
  return (
    <thead className={COLUMN_HEADER_CLASS}>
      <tr>
        <th className="px-1 py-1.5 w-12 sm:w-16 text-center align-bottom sticky left-0 z-20 bg-canvas">
          {cornerLabel}
        </th>
        {teams.map((team) => (
          <th key={team.teamId} className="px-2 py-1.5 min-w-[90px] text-left align-bottom bg-header-band text-on-fill">
            <div className="font-normal">{team.number}</div>
            <div className="text-sm font-bold">{team.name}</div>
          </th>
        ))}
      </tr>
    </thead>
  );
}
