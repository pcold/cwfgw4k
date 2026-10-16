/**
 * U+2212, set where `Intl` writes an ASCII hyphen, so that a signed column's `+` and `−` are the
 * same width. A surface whose font has no glyph for it substitutes its own.
 */
export const MINUS_SIGN = '−';

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

/**
 * Display only: the server keeps exact amounts, so a column of these can miss its total by a cent.
 * Season totals take {@link formatWholeDollars} instead.
 */
export function formatDollarsAndCents(value: number): string {
  return formatSigned(dollarsAndCents, value);
}

/** @see formatDollarsAndCents */
export function formatWholeDollars(value: number): string {
  return formatSigned(wholeDollars, value);
}

// `exceptZero` leaves the sign off exactly when the value rounds to zero, which is when the
// whole string collapses to `$0` rather than `$0.00`.
function formatSigned(format: Intl.NumberFormat, value: number): string {
  const parts = format.formatToParts(value);
  const signed = parts.some((part) => part.type === 'plusSign' || part.type === 'minusSign');
  if (!signed) return ZERO;
  return parts.map((part) => (part.type === 'minusSign' ? MINUS_SIGN : part.value)).join('');
}
