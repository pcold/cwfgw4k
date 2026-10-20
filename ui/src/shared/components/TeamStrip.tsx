import type { ReactElement } from 'react';
import { TAP_TARGET_CLASS } from './formFields';

/** @see TeamStrip */
export interface TeamStripTeam {
  readonly teamId: string;
  readonly teamName: string;
}

// The end padding is as wide as the fade, so the last chip can scroll clear of it.
const STRIP_CLASS = 'flex gap-2 overflow-x-auto pr-8 mask-r-from-[calc(100%-2rem)]';

const CHIP_CLASS =
  `shrink-0 whitespace-nowrap rounded border px-3 font-display font-bold text-sm uppercase tracking-wider ${TAP_TARGET_CLASS}`;

const SELECTED_CLASS = 'bg-chrome border-chrome text-on-chrome';

const IDLE_CLASS = 'bg-surface-card border-hairline-strong text-ink';

interface TeamStripProps {
  readonly teams: readonly TeamStripTeam[];
  readonly selectedTeamId: string;
  readonly onSelect: (teamId: string) => void;
}

/** An id matching none of `teams` leaves every chip unpressed, so the caller resolves it to a team first. */
export default function TeamStrip({ teams, selectedTeamId, onSelect }: TeamStripProps): ReactElement {
  return (
    <div role="group" aria-label="Team" className={STRIP_CLASS}>
      {teams.map((team) => {
        const selected = team.teamId === selectedTeamId;
        return (
          <button
            key={team.teamId}
            type="button"
            aria-pressed={selected}
            onClick={() => onSelect(team.teamId)}
            className={`${CHIP_CLASS} ${selected ? SELECTED_CLASS : IDLE_CLASS}`}
          >
            {team.teamName}
          </button>
        );
      })}
    </div>
  );
}
