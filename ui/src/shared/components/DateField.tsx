import type { ReactElement } from 'react';
import FieldShell from './FieldShell';
import { FIELD_CLASS } from './formControls';

interface DateFieldProps {
  readonly id: string;
  readonly label: string;
  /** ISO `yyyy-MM-dd`, or `''` when unset — the native date input's own value format. */
  readonly value: string;
  readonly error: string | undefined;
  readonly optional?: boolean;
  /** Marks the control required for assistive tech. Validation stays with the caller's `error`. */
  readonly required?: boolean;
  readonly width?: string;
  readonly onChange: (value: string) => void;
  readonly onBlur?: () => void;
}

/**
 * The native picker's only malformed value is empty, so the caller owns everything past the shape —
 * required, and whether the day named is one their domain accepts.
 */
export default function DateField({
  id,
  label,
  value,
  error,
  optional,
  required,
  width,
  onChange,
  onBlur,
}: DateFieldProps): ReactElement {
  return (
    <FieldShell id={id} label={label} error={error} optional={optional} width={width}>
      <input
        id={id}
        type="date"
        value={value}
        required={required}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? `${id}-error` : undefined}
        className={FIELD_CLASS}
      />
    </FieldShell>
  );
}
