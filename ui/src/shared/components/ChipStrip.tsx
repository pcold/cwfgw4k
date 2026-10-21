import type { ReactElement } from 'react';
import { TAP_TARGET_CLASS } from './formFields';

/** @see ChipStrip */
export interface StripChip {
  readonly id: string;
  readonly label: string;
}

// The end padding is as wide as the fade, so the last chip can scroll clear of it.
const STRIP_CLASS = 'flex gap-2 overflow-x-auto pr-8 mask-r-from-[calc(100%-2rem)]';

const CHIP_CLASS =
  `shrink-0 whitespace-nowrap rounded border px-3 font-display font-bold text-sm uppercase tracking-wider ${TAP_TARGET_CLASS}`;

const SELECTED_CLASS = 'bg-chrome border-chrome text-on-chrome';

const IDLE_CLASS = 'bg-surface-card border-hairline-strong text-ink';

interface ChipStripProps {
  readonly groupLabel: string;
  readonly chips: readonly StripChip[];
  readonly selectedId: string;
  readonly onSelect: (id: string) => void;
}

/**
 * `aria-pressed` buttons, not a `tablist`, which promises arrow-key handling this has none of. An id matching
 * none of `chips` leaves every chip unpressed, so the caller resolves it to a chip first.
 */
export default function ChipStrip({ groupLabel, chips, selectedId, onSelect }: ChipStripProps): ReactElement {
  return (
    <div role="group" aria-label={groupLabel} className={STRIP_CLASS}>
      {chips.map((chip) => {
        const selected = chip.id === selectedId;
        return (
          <button
            key={chip.id}
            type="button"
            aria-pressed={selected}
            onClick={() => onSelect(chip.id)}
            className={`${CHIP_CLASS} ${selected ? SELECTED_CLASS : IDLE_CLASS}`}
          >
            {chip.label}
          </button>
        );
      })}
    </div>
  );
}
