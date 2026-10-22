import { describe, expect, it } from 'vitest';
import {
  formatDollarsAndCents,
  formatUnsignedDollarsAndCents,
  formatWholeDollars,
  moneyAmount,
  moneyTone,
  moneyToneClass,
} from './money';

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

describe('formatUnsignedDollarsAndCents', () => {
  it('rounds to cents and never signs', () => {
    expect(formatUnsignedDollarsAndCents(20)).toBe('$20.00');
    expect(formatUnsignedDollarsAndCents(18.333)).toBe('$18.33');
    expect(formatUnsignedDollarsAndCents(1500)).toBe('$1,500.00');
  });

  it('renders zero, and anything that rounds to zero, as "$0" like the signed forms', () => {
    expect(formatUnsignedDollarsAndCents(0)).toBe('$0');
    expect(formatUnsignedDollarsAndCents(0.004)).toBe('$0');
  });
});

describe('moneyTone', () => {
  it('is even exactly where the cents formatter prints "$0", and signed from the first cent', () => {
    expect(moneyTone(formatDollarsAndCents(0))).toBe('even');
    expect(moneyTone(formatDollarsAndCents(0.004))).toBe('even');
    expect(moneyTone(formatDollarsAndCents(-0.004))).toBe('even');
    expect(moneyTone(formatDollarsAndCents(0.01))).toBe('up');
    expect(moneyTone(formatDollarsAndCents(-0.01))).toBe('down');
  });

  it('is even exactly where the whole-dollar formatter prints "$0", and signed from the first dollar', () => {
    expect(moneyTone(formatWholeDollars(0.4))).toBe('even');
    expect(moneyTone(formatWholeDollars(-0.49))).toBe('even');
    expect(moneyTone(formatWholeDollars(0.5))).toBe('up');
    expect(moneyTone(formatWholeDollars(-0.5))).toBe('down');
  });

  it('keeps a cents amount that a whole-dollar rounding would zero signed', () => {
    expect(moneyTone(formatDollarsAndCents(0.4))).toBe('up');
    expect(moneyTone(formatDollarsAndCents(-0.4))).toBe('down');
  });

  it('reads a PDF en dash as a loss like the screen minus', () => {
    expect(moneyTone('\u2013$80')).toBe('down');
  });

  it('gives an unsigned amount no tone', () => {
    expect(moneyTone(formatUnsignedDollarsAndCents(20))).toBe('even');
  });
});

describe('moneyToneClass', () => {
  it('colours a gain and a loss, and mutes an even amount', () => {
    expect(moneyToneClass('+$1')).toBe('text-semantic-success');
    expect(moneyToneClass('\u2212$1')).toBe('text-semantic-danger');
    expect(moneyToneClass('$0')).toBe('text-ink-soft');
  });
});

describe('moneyAmount', () => {
  it('reads the amount back out of the text, signed by its tone', () => {
    expect(moneyAmount('+$1,082.40')).toBe(1082.4);
    expect(moneyAmount('\u2212$35')).toBe(-35);
    expect(moneyAmount('$0')).toBe(0);
  });

  it('compares two amounts at the precision they print', () => {
    expect(moneyAmount(formatWholeDollars(1082.4))).toBe(moneyAmount(formatWholeDollars(1082.1)));
  });
});
