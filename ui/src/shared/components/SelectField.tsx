import type { ReactElement } from 'react';
import FieldShell from './FieldShell';
import { FIELD_CLASS, type SelectOption } from './formControls';

interface SelectFieldProps {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly error: string | undefined;
  readonly optional?: boolean;
  /** Marks the control required for assistive tech. Validation stays with the caller's `error`. */
  readonly required?: boolean;
  readonly placeholder: string;
  readonly options: readonly SelectOption[];
  readonly width?: string;
  readonly onChange: (value: string) => void;
  readonly onBlur?: () => void;
}

/** The placeholder option is disabled, so it offers no way back to unset — see `FieldShell`. */
export default function SelectField({
  id,
  label,
  value,
  error,
  optional,
  required,
  placeholder,
  options,
  width,
  onChange,
  onBlur,
}: SelectFieldProps): ReactElement {
  return (
    <FieldShell id={id} label={label} error={error} optional={optional} width={width}>
      <select
        id={id}
        value={value}
        required={required}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? `${id}-error` : undefined}
        className={FIELD_CLASS}
      >
        <option value="" disabled>
          {placeholder}
        </option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}
