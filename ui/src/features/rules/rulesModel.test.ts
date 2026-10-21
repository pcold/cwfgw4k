import { describe, expect, it } from 'vitest';
import { payoutsByPlace } from './rulesModel';

describe('payoutsByPlace', () => {
  it('numbers each amount by its finishing place, from 1', () => {
    expect(payoutsByPlace([18, 12, 10])).toEqual([
      { place: 1, amount: 18 },
      { place: 2, amount: 12 },
      { place: 3, amount: 10 },
    ]);
  });

  it('has no places for a season with no payouts', () => {
    expect(payoutsByPlace([])).toEqual([]);
  });
});
