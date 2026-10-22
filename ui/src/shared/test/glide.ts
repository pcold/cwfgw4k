import { act } from '@testing-library/react';
import { onTestFinished, vi } from 'vitest';

const ROW_HEIGHT = 40;

/**
 * jsdom lays nothing out, so every box measures zero and a reorder moves nothing for motion to animate. This stacks
 * each element by its index among its siblings, for the rest of the test.
 */
export function stackBySiblingIndex(): void {
  const spy = vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function stacked(this: Element) {
    const siblings = this.parentElement ? Array.from(this.parentElement.children) : [];
    return DOMRect.fromRect({ x: 0, y: siblings.indexOf(this) * ROW_HEIGHT, width: 100, height: ROW_HEIGHT });
  });
  onTestFinished(() => spy.mockRestore());
}

/** Motion measures and animates on animation frames, which jsdom runs on a timer. */
export async function elapseFrames(): Promise<void> {
  await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
}

/** A glide plays as a translate from the old place; a remounted row has no transform, a reduced-motion one `none`. */
export function isGliding(row: HTMLElement): boolean {
  return row.style.transform.startsWith('translate');
}
