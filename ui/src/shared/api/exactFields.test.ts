import { describe, expect, it } from 'vitest';
import { camelizeKeys } from './client';
import type { CamelizedKey, ExactFields, ExactValues } from './exactFields';

// The verdicts are checked by tsc, not at runtime: each `@ts-expect-error` fails the typecheck if the
// line beneath it ever compiles, and each `true` annotation fails it if the verdict stops being `true`.
describe('ExactFields', () => {
  it('accepts a type whose keys are the wire keys, camelized', () => {
    const verdict: ExactFields<{ teamName: string; top10Count: number }, 'team_name' | 'top_10_count'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses a key renamed on the wire', () => {
    // @ts-expect-error verdict: 'teamName' is not on the wire, 'name' is absent from types.ts
    const verdict: ExactFields<{ teamName: string }, 'name'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses a key the wire gained', () => {
    // @ts-expect-error verdict: 'ownerName' is absent from types.ts
    const verdict: ExactFields<{ teamName: string }, 'team_name' | 'owner_name'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses a key the wire dropped', () => {
    // @ts-expect-error verdict: 'ownerName' is not on the wire
    const verdict: ExactFields<{ teamName: string; ownerName: string }, 'team_name'> = true;

    expect(verdict).toBe(true);
  });

  it('accepts an optional key the wire sends', () => {
    const verdict: ExactFields<{ teamName?: string }, 'team_name'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses an optional key the wire no longer sends', () => {
    // @ts-expect-error verdict: an optional 'ownerName' is still not on the wire
    const verdict: ExactFields<{ teamName: string; ownerName?: string }, 'team_name'> = true;

    expect(verdict).toBe(true);
  });

  it('accepts a nullable key, which the wire sends as an explicit null', () => {
    const verdict: ExactFields<{ teamName: string | null }, 'team_name'> = true;

    expect(verdict).toBe(true);
  });

  it('reads every arm of a union, so one arm cannot hide a key another lacks', () => {
    // @ts-expect-error verdict: the second arm's 'rank' is not on the wire
    const verdict: ExactFields<{ type: 'a' } | { type: 'b'; rank: number }, 'type'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses a sealed variant types.ts does not declare', () => {
    type Standing = { type: 'leader' } | { type: 'chaser' };
    // @ts-expect-error verdict: no such variant in types.ts
    const verdict: ExactFields<Extract<Standing, { type: 'trailer' }>, 'type'> = true;

    expect(verdict).toBe(true);
  });
});

describe('ExactValues', () => {
  it('accepts a union holding exactly the serial names', () => {
    const verdict: ExactValues<'season' | 'tournament', 'season' | 'tournament'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses a value the enum gained', () => {
    // @ts-expect-error verdict: 'weekly' is absent from types.ts
    const verdict: ExactValues<'season' | 'tournament', 'season' | 'tournament' | 'weekly'> = true;

    expect(verdict).toBe(true);
  });

  it('refuses a value the enum dropped', () => {
    // @ts-expect-error verdict: 'tournament' is not on the wire
    const verdict: ExactValues<'season' | 'tournament', 'season'> = true;

    expect(verdict).toBe(true);
  });
});

describe('CamelizedKey', () => {
  it('renames each key the way camelizeKeys does at runtime', () => {
    const wire = { position_str: 0, top_10_count: 0, a__b: 0, x_Y: 0, trailing_: 0, plain: 0 };
    const expected: { [K in keyof typeof wire]: CamelizedKey<K> } = {
      position_str: 'positionStr',
      top_10_count: 'top10Count',
      a__b: 'a_B',
      x_Y: 'x_Y',
      trailing_: 'trailing_',
      plain: 'plain',
    };

    expect(Object.keys(camelizeKeys(wire) ?? {})).toEqual(Object.values(expected));
  });
});
