import { describe, it, expect } from 'vitest';
import { nth } from './nth';

describe('nth', () => {
  it('returns the element at the requested index', () => {
    expect(nth(['first', 'second', 'third'], 1)).toBe('second');
  });

  it('reports how many matches there actually were when the index is past the end', () => {
    expect(() => nth(['only'], 1)).toThrow('Expected a match at index 1, but the query returned only 1.');
  });

  it('throws rather than answering undefined for an empty match list', () => {
    expect(() => nth([], 0)).toThrow('Expected a match at index 0, but the query returned only 0.');
  });
});
