import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readStoredMyTeam, writeStoredMyTeam } from './myTeamStorage';

describe('myTeamStorage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('writes, reads back and removes the cwfg.myTeam entry', () => {
    writeStoredMyTeam('{"kind":"browsing"}');
    expect(window.localStorage.getItem('cwfg.myTeam')).toBe('{"kind":"browsing"}');
    expect(readStoredMyTeam()).toBe('{"kind":"browsing"}');

    writeStoredMyTeam(null);
    expect(window.localStorage.getItem('cwfg.myTeam')).toBeNull();
  });

  it('reads nothing when storage throws', () => {
    window.localStorage.setItem('cwfg.myTeam', '{"kind":"browsing"}');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError');
    });

    expect(readStoredMyTeam()).toBeNull();
  });

  it.each([
    ['setItem', '{"kind":"browsing"}'],
    ['removeItem', null],
  ] as const)('swallows a throw from %s and leaves the entry as it was', (method, serialised) => {
    window.localStorage.setItem('cwfg.myTeam', '{"kind":"team","teamName":"WOMBLE"}');
    vi.spyOn(Storage.prototype, method).mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError');
    });

    expect(() => writeStoredMyTeam(serialised)).not.toThrow();
    vi.restoreAllMocks();
    expect(window.localStorage.getItem('cwfg.myTeam')).toBe('{"kind":"team","teamName":"WOMBLE"}');
  });
});
