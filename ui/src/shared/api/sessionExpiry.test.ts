import { describe, expect, it, vi } from 'vitest';
import { notifySessionExpired, onSessionExpired } from './sessionExpiry';

describe('onSessionExpired', () => {
  it('notifies every subscriber', () => {
    const first = vi.fn();
    const second = vi.fn();
    const unsubscribeFirst = onSessionExpired(first);
    const unsubscribeSecond = onSessionExpired(second);

    notifySessionExpired();

    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);
    unsubscribeFirst();
    unsubscribeSecond();
  });

  // The subscriber is AuthProvider, which StrictMode mounts twice; an unsubscribe that did not take
  // would leave a handler holding a discarded query client.
  it('stops notifying a subscriber that unsubscribed', () => {
    const handler = vi.fn();
    const unsubscribe = onSessionExpired(handler);

    unsubscribe();
    notifySessionExpired();

    expect(handler).not.toHaveBeenCalled();
  });
});
