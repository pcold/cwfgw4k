import type { ReactNode } from 'react';
import { CONTROL_CLASS, TAP_TARGET_CLASS, type SelectOption } from './formControls';

const PICKER_SELECT_CLASS = `bg-surface-card ${CONTROL_CLASS} ${TAP_TARGET_CLASS} w-full min-w-0 truncate sm:w-auto`;

interface PickerRowProps {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly options: readonly SelectOption[];
  readonly onChange: (value: string) => void;
  /** Not options: the controls after the select, such as the live-overlay toggle, which wrap with it. */
  readonly children?: ReactNode;
}

/**
 * Below `sm:` the select takes a line of its own and truncates a long option rather than widen the page;
 * from `sm:` it sizes to its options.
 */
function PickerRow({ id, label, value, options, onChange, children }: PickerRowProps): ReactNode {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <label htmlFor={id} className="text-xs text-ink-muted uppercase tracking-wider">
        {label}
      </label>
      <select id={id} className={PICKER_SELECT_CLASS} value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {children}
    </div>
  );
}

export default PickerRow;
