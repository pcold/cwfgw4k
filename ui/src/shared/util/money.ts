/**
 * U+2212, set where `Intl` writes an ASCII hyphen, so that a signed column's `+` and `−` are the
 * same width. A surface whose font has no glyph for it substitutes its own.
 */
export const MINUS_SIGN = '\u2212';

const ZERO = '$0';

const dollarsAndCents = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  signDisplay: 'exceptZero',
});

const wholeDollars = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  signDisplay: 'exceptZero',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

const unsignedDollarsAndCents = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  signDisplay: 'never',
});

/**
 * Display only: the server keeps exact amounts, so a rounded column can visibly miss its total.
 * Season totals take {@link formatWholeDollars}, and amounts a rule sets in advance
 * {@link formatUnsignedDollarsAndCents}.
 */
export function formatDollarsAndCents(value: number): string {
  return formatSigned(dollarsAndCents, value);
}

/** @see formatDollarsAndCents */
export function formatWholeDollars(value: number): string {
  return formatSigned(wholeDollars, value);
}

/**
 * A sign would read a stake or rate as money won. `never` drops a minus too, which is safe only because
 * the server refuses a negative rule amount.
 */
export function formatUnsignedDollarsAndCents(value: number): string {
  const formatted = unsignedDollarsAndCents.format(value);
  return formatted === '$0.00' ? ZERO : formatted;
}

/** @see moneyTone */
export type MoneyTone = 'up' | 'down' | 'even';

/**
 * Takes the formatted text, not the number, so the tone is decided at the column's own precision: a
 * cents column's `+$0.40` is `up` where a whole-dollar rounding would call it `even`. Reads the sign a
 * signed formatter or its PDF twin puts before the `$` — a minus of either glyph is `down`.
 */
export function moneyTone(display: string): MoneyTone {
  if (display.startsWith('+')) return 'up';
  if (display.startsWith('$')) return 'even';
  return 'down';
}

/** @see moneyTone; the amount at the precision the text prints, so two figures that print alike compare equal. */
export function moneyAmount(display: string): number {
  const magnitude = Number(display.replace(/[^\d.]/g, ''));
  return moneyTone(display) === 'down' ? -magnitude : magnitude;
}

const MONEY_TONE_CLASS: Readonly<Record<MoneyTone, string>> = {
  up: 'text-semantic-success',
  down: 'text-semantic-danger',
  even: 'text-ink-soft',
};

/** Only a surface's bottom line takes it; every other money cell keeps its table's ink. */
export function moneyToneClass(display: string): string {
  return MONEY_TONE_CLASS[moneyTone(display)];
}

// `exceptZero` leaves the sign off exactly when the value rounds to zero, which is when the
// whole string collapses to `$0` rather than `$0.00`.
function formatSigned(format: Intl.NumberFormat, value: number): string {
  const parts = format.formatToParts(value);
  const signed = parts.some((part) => part.type === 'plusSign' || part.type === 'minusSign');
  if (!signed) return ZERO;
  return parts.map((part) => (part.type === 'minusSign' ? MINUS_SIGN : part.value)).join('');
}
