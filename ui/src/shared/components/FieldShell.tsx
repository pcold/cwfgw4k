import type { ReactElement, ReactNode } from 'react';

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
 * The escape hatch for a control `TextField`, `DateField` and `SelectField` can't render: a `type="number"`,
 * a file input, or a select with no disabled placeholder. The caller owns wiring the control to `id` and
 * `aria-invalid` / `aria-describedby` to `${id}-error`.
 */
export default function FieldShell({
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
