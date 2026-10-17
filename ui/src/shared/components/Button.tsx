import type { MouseEventHandler, ReactElement, ReactNode } from 'react';
import { TAP_TARGET_CLASS } from './formFields';

/** @see Button */
export type ButtonTone = 'primary' | 'secondary' | 'danger';

/** @see Button */
export type ButtonSize = 'regular' | 'compact';

/**
 * Closed, so a caller can't append a second fill or padding: two unprefixed utilities of one property
 * resolve by stylesheet order, not by class order. A new layout need joins the list here.
 */
export type ButtonLayout = 'flex-1' | 'mt-6' | 'whitespace-nowrap';

const BASE_CLASS = `rounded font-medium disabled:bg-disabled ${TAP_TARGET_CLASS}`;

const TONE_CLASS: Readonly<Record<ButtonTone, string>> = {
  primary: 'bg-primary hover:bg-primary-hover text-on-primary',
  secondary: 'bg-secondary hover:bg-secondary-hover text-on-fill',
  danger: 'bg-semantic-danger-fill hover:bg-semantic-danger-fill-hover text-on-fill',
};

/** `compact` sits in a header row or panel row; `TAP_TARGET_CLASS` still holds it at 44px tall. */
const SIZE_CLASS: Readonly<Record<ButtonSize, string>> = {
  regular: 'px-4 py-2 text-sm',
  compact: 'px-3 py-1 text-xs',
};

interface ButtonProps {
  readonly tone: ButtonTone;
  readonly size: ButtonSize;
  readonly type?: 'button' | 'submit';
  readonly layout?: ButtonLayout;
  readonly disabled?: boolean;
  readonly onClick?: MouseEventHandler<HTMLButtonElement>;
  readonly 'aria-describedby'?: string;
  readonly children: ReactNode;
}

/**
 * Every filled button in the app; a text button stays a plain `<button>`. No `className`, so the fill and
 * padding come from here alone — ESLint fails a filled tone's background spelled anywhere else.
 */
export default function Button({
  tone,
  size,
  type = 'button',
  layout,
  disabled,
  onClick,
  'aria-describedby': ariaDescribedBy,
  children,
}: ButtonProps): ReactElement {
  const className = [BASE_CLASS, TONE_CLASS[tone], SIZE_CLASS[size], layout].filter(Boolean).join(' ');
  return (
    <button type={type} disabled={disabled} onClick={onClick} aria-describedby={ariaDescribedBy} className={className}>
      {children}
    </button>
  );
}
