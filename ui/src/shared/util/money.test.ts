import { describe, expect, it } from 'vitest';
import { formatMoney } from './money';

describe('formatMoney', () => {
  it('renders zero as "$0" without decimals regardless of override', () => {
    expect(formatMoney(0)).toBe('$0');
    expect(formatMoney(0, 2)).toBe('$0');
  });

  it('auto-detects two decimals for whole and half-dollar values', () => {
    expect(formatMoney(120)).toBe('$120.00');
    expect(formatMoney(-80)).toBe('-$80.00');
    expect(formatMoney(18.5)).toBe('$18.50');
  });

  it('auto-detects three decimals when the value has tenths of a cent', () => {
    expect(formatMoney(18.333)).toBe('$18.333');
  });

  it('auto-detects four decimals for finer splits', () => {
    expect(formatMoney(18.1234)).toBe('$18.1234');
  });

  it('honours an explicit decimals override (used by Team Standings)', () => {
    expect(formatMoney(120.75, 0)).toBe('$121');
    expect(formatMoney(-80.4, 0)).toBe('-$80');
    expect(formatMoney(1500000, 0)).toBe('$1500000');
  });

  it('omits thousand separators to match the legacy Alpine UI', () => {
    expect(formatMoney(1500000)).toBe('$1500000.00');
  });
});
