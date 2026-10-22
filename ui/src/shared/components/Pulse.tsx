import { useState, type AnimationEvent, type ReactElement } from 'react';
import { formatDollarsAndCents } from '@/shared/util/money';

/** `new` is a change with no direction: a value appearing or vanishing, or a new label on the same measure. */
export type PulseTone = 'up' | 'down' | 'new';

/** Money and a top-10 count read better higher; a finish and a score to par, lower. */
export type PulseBetter = 'higher' | 'lower';

/** The element that carries the figure takes it beside `usePulse`'s attributes. */
export const PULSE_CLASS =
  'data-[pulse=up]:animate-pulse-up data-[pulse=down]:animate-pulse-down data-[pulse=new]:animate-pulse-new';

interface Reading {
  readonly scope: string | undefined;
  readonly display: string;
  readonly measure: number | null;
}

export interface PulseAttributes {
  readonly 'data-pulse': PulseTone | undefined;
  readonly onAnimationEnd: (event: AnimationEvent) => void;
}

function toneOf(previous: Reading, next: Reading, better: PulseBetter): PulseTone {
  if (previous.measure === null || next.measure === null || previous.measure === next.measure) return 'new';
  return next.measure > previous.measure === (better === 'higher') ? 'up' : 'down';
}

/**
 * Pulses when `display` changes under the same `scope`; unset, never. Pass the list's selection key, plus whatever
 * else swaps the figure in place (the phone report's team), so only a refresh of the same data pulses.
 */
export function usePulse(
  display: string,
  measure: number | null,
  better: PulseBetter,
  scope: string | undefined,
): PulseAttributes {
  // State set during render rather than a ref, which React forbids reading in render, or an effect, which would
  // paint the new figure once without its pulse.
  const [shown, setShown] = useState<Reading>({ scope, display, measure });
  const [tone, setTone] = useState<PulseTone | undefined>(undefined);
  if (shown.scope !== scope || shown.display !== display) {
    const next = { scope, display, measure };
    setShown(next);
    setTone(scope !== undefined && shown.scope === scope ? toneOf(shown, next, better) : undefined);
  }
  return {
    'data-pulse': tone,
    // A pulse nested inside this one ends first, and its event bubbles.
    onAnimationEnd: (event) => {
      if (event.target === event.currentTarget) setTone(undefined);
    },
  };
}

interface PulseProps {
  readonly display: string;
  readonly measure: number | null;
  readonly better: PulseBetter;
  /** @see usePulse */
  readonly scope: string | undefined;
}

/** A figure inside a cell of its own: the highlight sits behind the text and leaves the cell's fill alone. */
export default function Pulse({ display, measure, better, scope }: PulseProps): ReactElement {
  return (
    <span className={`rounded-sm px-0.5 -mx-0.5 ${PULSE_CLASS}`} {...usePulse(display, measure, better, scope)}>
      {display}
    </span>
  );
}

/** @see Pulse */
export function PulseDollars({ amount, scope }: { amount: number; scope: string | undefined }): ReactElement {
  return <Pulse display={formatDollarsAndCents(amount)} measure={amount} better="higher" scope={scope} />;
}
