import { describe, expect, it } from 'vitest';
import { formatDollarsAndCents, formatWholeDollars } from './money';

describe('formatDollarsAndCents', () => {
  it('rounds to cents and signs a gain with a plus', () => {
    expect(formatDollarsAndCents(751.5715)).toBe('+$751.57');
    expect(formatDollarsAndCents(18.5)).toBe('+$18.50');
    expect(formatDollarsAndCents(120)).toBe('+$120.00');
  });

  it('signs a loss with a true minus, U+2212, not a hyphen', () => {
    expect(formatDollarsAndCents(-1.7785)).toBe('\u2212$1.78');
  });

  it('groups thousands', () => {
    expect(formatDollarsAndCents(1500000)).toBe('+$1,500,000.00');
  });

  it('renders zero, and anything that rounds to zero, as an unsigned "$0"', () => {
    expect(formatDollarsAndCents(0)).toBe('$0');
    expect(formatDollarsAndCents(0.004)).toBe('$0');
    expect(formatDollarsAndCents(-0.004)).toBe('$0');
  });
});

describe('formatWholeDollars', () => {
  it('rounds to whole dollars and groups thousands', () => {
    expect(formatWholeDollars(1082)).toBe('+$1,082');
    expect(formatWholeDollars(120.75)).toBe('+$121');
    expect(formatWholeDollars(-80.4)).toBe('\u2212$80');
  });

  it('renders zero, and anything that rounds to zero, as an unsigned "$0"', () => {
    expect(formatWholeDollars(0)).toBe('$0');
    expect(formatWholeDollars(0.4)).toBe('$0');
    expect(formatWholeDollars(-0.4)).toBe('$0');
  });
});
