import type { ReactElement, ReactNode } from 'react';

const FIELD_CLASS = 'bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full';

interface FieldShellProps {
  readonly id: string;
  readonly label: string;
  readonly error: string | undefined;
  readonly children: ReactNode;
}

/**
 * The escape hatch for a control `TextField` can't render. The caller owns wiring the control to `id`
 * and `aria-invalid` / `aria-describedby` to `${id}-error`.
 */
export function FieldShell({ id, label, error, children }: FieldShellProps): ReactElement {
  return (
    <div>
      <label htmlFor={id} className="block text-xs text-gray-400 mb-1">
        {label}
      </label>
      {children}
      {error !== undefined && (
        <p id={`${id}-error`} role="alert" className="mt-1 text-red-400 text-xs">
          {error}
        </p>
      )}
    </div>
  );
}

interface TextFieldProps {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly error: string | undefined;
  readonly type?: 'text' | 'password';
  readonly placeholder?: string;
  /** For a modal's first field; `ModalShell` then leaves focus on it rather than on the panel. */
  readonly initialFocus?: boolean;
  readonly onChange: (value: string) => void;
}

/** @see FieldShell */
export function TextField({
  id,
  label,
  value,
  error,
  type = 'text',
  placeholder,
  initialFocus,
  onChange,
}: TextFieldProps): ReactElement {
  return (
    <FieldShell id={id} label={label} error={error}>
      <input
        id={id}
        type={type}
        value={value}
        placeholder={placeholder}
        // eslint-disable-next-line jsx-a11y/no-autofocus -- only a modal's first field asks; see `initialFocus`.
        autoFocus={initialFocus}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? `${id}-error` : undefined}
        className={FIELD_CLASS}
      />
    </FieldShell>
  );
}
