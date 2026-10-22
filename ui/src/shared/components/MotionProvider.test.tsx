import { useState, type ReactNode } from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { m } from 'motion/react';
import { elapseFrames, isGliding, stackBySiblingIndex } from '@/shared/test/glide';
import { GLIDE_LAYOUT } from './ListCard';
import MotionProvider from './MotionProvider';

/** Motion reads the media query once per module and then only listens, so each test flips it through the listener. */
const reducedMotion = { matches: false, listeners: new Set<() => void>() };

function preferReducedMotion(matches: boolean): void {
  reducedMotion.matches = matches;
  reducedMotion.listeners.forEach((listener) => listener());
}

function Standings(): ReactNode {
  const [order, setOrder] = useState(['Aces', 'Birdies']);
  return (
    <>
      <button type="button" onClick={() => setOrder(['Birdies', 'Aces'])}>
        Refetch
      </button>
      <ol>
        {order.map((name) => (
          <m.li key={name} layout={GLIDE_LAYOUT}>
            {name}
          </m.li>
        ))}
      </ol>
    </>
  );
}

async function reorder(): Promise<HTMLElement> {
  stackBySiblingIndex();
  const user = userEvent.setup();
  render(
    <MotionProvider>
      <Standings />
    </MotionProvider>,
  );
  await elapseFrames();
  await user.click(screen.getByRole('button', { name: 'Refetch' }));
  await elapseFrames();
  return screen.getByText('Aces');
}

describe('MotionProvider', () => {
  beforeAll(() => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      media: query,
      get matches() {
        return reducedMotion.matches;
      },
      addEventListener: (_type: string, listener: () => void) => reducedMotion.listeners.add(listener),
      removeEventListener: (_type: string, listener: () => void) => reducedMotion.listeners.delete(listener),
    }));
    return () => vi.unstubAllGlobals();
  });

  it('glides a reordered row', async () => {
    preferReducedMotion(false);

    expect(isGliding(await reorder())).toBe(true);
  });

  it('moves a reordered row without gliding for a visitor who asks for reduced motion', async () => {
    preferReducedMotion(true);

    expect(isGliding(await reorder())).toBe(false);
  });
});
