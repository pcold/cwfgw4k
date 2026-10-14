type SessionExpiredHandler = () => void;

const handlers = new Set<SessionExpiredHandler>();

/** Keeps `client/core.ts` free of auth state: the subscriber owns what an expiry does. */
export function onSessionExpired(handler: SessionExpiredHandler): () => void {
  handlers.add(handler);
  return () => {
    handlers.delete(handler);
  };
}

/** Called once per refused request; only the subscriber holds the state to collapse a burst of them. */
export function notifySessionExpired(): void {
  handlers.forEach((handler) => {
    handler();
  });
}
