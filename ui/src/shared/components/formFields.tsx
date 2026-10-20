import type { ReactElement, ReactNode } from 'react';

/** @see SelectField */
export interface SelectOption {
  readonly value: string;
  readonly label: string;
}

/**
 * `text-base`, not the body's `text-sm`: iOS zooms the page into any control under 16px. The surface and
 * width are left to the caller, since a form field and a toolbar select differ in both.
 */
export const CONTROL_CLASS = 'border border-control-border rounded px-3 py-2 text-base';

/**
 * 44px, the smallest target a thumb reliably hits. A button or form control centres its content in the
 * height; a link needs `flex items-center` beside it, or its text sits at the top.
 */
export const TAP_TARGET_CLASS = 'min-h-11';

/**
 * Its `w-full` fills the field's box, so size a field through `width` rather than appending a width
 * here: two unprefixed widths on one control resolve by stylesheet order, not by class order.
 */
export const FIELD_CLASS = `bg-surface-raised ${CONTROL_CLASS} ${TAP_TARGET_CLASS} w-full`;

interface FieldShellProps {
  readonly id: string;
  readonly label: string;
  readonly error: string | undefined;
  readonly optional?: boolean;
  /**
   * Sizes the box the label sits in too, and the control fills it. Unset, the box is a plain block: its
   * contents' width in a flex row, but the container's full width in block flow.
   */
  readonly width?: string;
  /**
   * For a control a table column heads, or whose options name what it picks; the label still names it for
   * assistive tech.
   */
  readonly hideLabel?: boolean;
  readonly children: ReactNode;
}

/**
 * The escape hatch for a control the three fields below can't render: a `type="number"`, a file input,
 * or a select with no disabled placeholder. The caller owns wiring the control to `id` and
 * `aria-invalid` / `aria-describedby` to `${id}-error`.
 */
export function FieldShell({
  id,
  label,
  error,
  optional = false,
  width,
  hideLabel = false,
  children,
}: FieldShellProps): ReactElement {
  return (
    <div className={width}>
      <label htmlFor={id} className={hideLabel ? 'sr-only' : 'block text-xs text-ink-muted mb-1'}>
        {label}
        {optional && ' (optional)'}
      </label>
      {children}
      {error !== undefined && (
        <p id={`${id}-error`} role="alert" className="mt-1 text-semantic-danger text-xs">
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
  readonly width?: string;
  /** For a modal's first field; `ModalShell` then leaves focus on it rather than on the panel. */
  readonly initialFocus?: boolean;
  readonly disabled?: boolean;
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
export function DateField({
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
export function SelectField({
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
