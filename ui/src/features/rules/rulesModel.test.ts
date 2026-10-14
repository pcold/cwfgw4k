import { describe, expect, it } from 'vitest';
import { ordinal } from './rulesModel';

describe('ordinal', () => {
  it('handles the regular suffixes', () => {
    expect(ordinal(1)).toBe('1st');
    expect(ordinal(2)).toBe('2nd');
    expect(ordinal(3)).toBe('3rd');
    expect(ordinal(4)).toBe('4th');
    expect(ordinal(10)).toBe('10th');
  });

  it('handles the teens as "th" regardless of the last digit', () => {
    expect(ordinal(11)).toBe('11th');
    expect(ordinal(12)).toBe('12th');
    expect(ordinal(13)).toBe('13th');
  });

  it('handles numbers in the 20s+ based on the last digit', () => {
    expect(ordinal(21)).toBe('21st');
    expect(ordinal(22)).toBe('22nd');
    expect(ordinal(103)).toBe('103rd');
  });
});
