// Mirrors the legacy Alpine UI `fmtMoney` so numeric output matches across
// both UIs during the migration. When `decimals` is omitted, auto-detects 2/3/4
// decimals so half-dollar ownership splits (e.g. $18.50, $18.333) don't round.
//
//   formatMoney(0)         === '$0'
//   formatMoney(120)       === '$120.00'
//   formatMoney(-80)       === '-$80.00'
//   formatMoney(18.333)    === '$18.333'
//   formatMoney(120, 0)    === '$120'
//   formatMoney(-80, 0)    === '-$80'
export function formatMoney(value: number, decimals?: number): string {
  const n = Number(value);
  if (n === 0) return '$0';
  const sign = n < 0 ? '-' : '';
  const abs = Math.abs(n);
  const digits = decimals ?? pickDecimals(abs);
  return `${sign}$${abs.toFixed(digits)}`;
}

function pickDecimals(abs: number): number {
  const cents = abs * 100;
  if (Math.abs(cents - Math.round(cents)) < 0.001) return 2;
  const mils = abs * 1000;
  if (Math.abs(mils - Math.round(mils)) < 0.001) return 3;
  return 4;
}
