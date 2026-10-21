const STORAGE_KEY = 'cwfg.myTeam';

/**
 * Null when storage is unavailable: private mode and blocked site data throw on access. Every read of the key goes
 * through here.
 */
export function readStoredMyTeam(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/**
 * Null removes the entry. A throw is swallowed: the caller keeps the choice in memory for this visit, and the next
 * visit asks again. Every write of the key goes through here.
 */
export function writeStoredMyTeam(serialised: string | null): void {
  try {
    if (serialised === null) {
      window.localStorage.removeItem(STORAGE_KEY);
    } else {
      window.localStorage.setItem(STORAGE_KEY, serialised);
    }
  } catch {
    // Private mode: the in-memory choice still holds for this visit.
  }
}
