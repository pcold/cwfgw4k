import { useState, type ReactNode } from 'react';
import Button from '@/shared/components/Button';
import { TAP_TARGET_CLASS } from '@/shared/components/formControls';
import ModalShell from '@/shared/components/ModalShell';
import { useMyTeam } from './MyTeamContext';

const TEAM_BUTTON_CLASS =
  `min-w-0 break-words rounded border-2 px-3 py-2 text-left font-display font-bold uppercase tracking-wider ${TAP_TARGET_CLASS}`;

const SELECTED_TEAM_CLASS = 'bg-chrome border-gold text-on-chrome';

const IDLE_TEAM_CLASS = 'bg-surface-raised border-hairline text-ink hover:border-hairline-strong';

const JUST_BROWSING_CLASS =
  `self-center sm:self-auto px-2 font-display font-semibold underline text-ink-muted hover:text-ink-strong ${TAP_TARGET_CLASS}`;

/** Mounted once beside the routes; opens itself on a first visit. */
export default function MyTeamPicker(): ReactNode {
  const { asking } = useMyTeam();
  if (!asking) return null;
  return <TeamPickerDialog />;
}

/**
 * Never blocks the page: a backdrop click, Escape or Close dismisses it for this visit without an answer. A team
 * button only selects; the primary button alone saves.
 */
function TeamPickerDialog(): ReactNode {
  const { teamNames, chooseTeam, chooseBrowsing, dismissPicker } = useMyTeam();
  const [selected, setSelected] = useState<string | null>(null);

  return (
    <ModalShell title="Which team is yours?" placement="responsive" onClose={dismissPicker} dismissOnBackdrop>
      {/* Bleeds through the shell's body padding, so the band spans the panel under its title row. */}
      <div className="-mx-5 -mt-4 mb-4 px-5 py-4 bg-header-band font-display">
        <p className="text-xs font-bold uppercase tracking-widest text-on-fill">Welcome to Castlewood</p>
        <p className="mt-1 text-base text-on-fill">
          We&apos;ll highlight your team and open the site on it. Saved on this device only, no login.
        </p>
      </div>
      <div role="group" aria-label="Teams" className="grid grid-cols-2 gap-2">
        {teamNames.map((teamName) => (
          <button
            key={teamName}
            type="button"
            aria-pressed={teamName === selected}
            onClick={() => setSelected(teamName)}
            className={`${TEAM_BUTTON_CLASS} ${teamName === selected ? SELECTED_TEAM_CLASS : IDLE_TEAM_CLASS}`}
          >
            {teamName}
          </button>
        ))}
      </div>
      <div className="mt-4 flex flex-col gap-2 sm:flex-row-reverse sm:items-center sm:justify-between">
        <Button
          tone="primary"
          size="regular"
          disabled={selected === null}
          onClick={() => {
            if (selected !== null) chooseTeam(selected);
          }}
        >
          <span className="font-display font-bold uppercase tracking-wider">
            {selected === null ? 'Set my team' : `Set ${selected} as my team`}
          </span>
        </Button>
        <button
          type="button"
          onClick={chooseBrowsing}
          className={JUST_BROWSING_CLASS}
        >
          Just browsing
        </button>
      </div>
      {/* The More sheet is the phone and tablet nav; from `lg` the header carries My team instead. */}
      <p className="mt-2 text-center sm:text-left text-sm font-display text-ink-muted">
        <span className="lg:hidden">
          Change anytime from <span className="font-semibold text-ink-secondary">More → My team</span>
        </span>
        <span className="hidden lg:inline">
          Change anytime from <span className="font-semibold text-ink-secondary">My team</span> in the header
        </span>
      </p>
    </ModalShell>
  );
}
