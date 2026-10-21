import type { ReactElement } from 'react';
import FieldShell from './FieldShell';
import { FIELD_CLASS } from './formControls';

interface TextFieldProps {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly error: string | undefined;
  readonly type?: 'text' | 'password';
  readonly placeholder?: string;
  readonly width?: string;
  /** For a modal's first field; `ModalShell` then leaves focus on it rather than on the panel. */
  readonly initialFocus?: boolean;
  readonly disabled?: boolean;
  readonly onChange: (value: string) => void;
}

/** @see FieldShell */
export default function TextField({
  id,
  label,
  value,
  error,
  type = 'text',
  placeholder,
  width,
  initialFocus,
  disabled,
  onChange,
}: TextFieldProps): ReactElement {
  return (
    <FieldShell id={id} label={label} error={error} width={width}>
      <input
        id={id}
        type={type}
        value={value}
        placeholder={placeholder}
        // eslint-disable-next-line jsx-a11y/no-autofocus -- only a modal's first field asks; see `initialFocus`.
        autoFocus={initialFocus}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? `${id}-error` : undefined}
        className={FIELD_CLASS}
      />
    </FieldShell>
  );
}
