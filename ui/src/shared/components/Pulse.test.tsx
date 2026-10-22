import { describe, expect, it } from 'vitest';
import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type ReactNode } from 'react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import Pulse, { PulseDollars, usePulse, type PulseBetter } from './Pulse';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

interface Reading {
  readonly display: string;
  readonly measure: number | null;
  readonly scope: string | undefined;
}

interface SteppingProps {
  readonly readings: readonly Reading[];
  readonly better?: PulseBetter;
}

function Stepping({ readings, better = 'higher' }: SteppingProps): ReactNode {
  const [step, setStep] = useState(0);
  const reading = readings[step];
  if (reading === undefined) return null;
  return (
    <>
      <button type="button" onClick={() => setStep(step + 1)}>
        Next
      </button>
      <Pulse display={reading.display} measure={reading.measure} better={better} scope={reading.scope} />
    </>
  );
}

function Nested({ amount }: { amount: number }): ReactNode {
  const display = `$${amount}`;
  return (
    <div {...usePulse(display, amount, 'higher', 's')}>
      <Pulse display={display} measure={amount} better="higher" scope="s" />
    </div>
  );
}

function SteppingNested(): ReactNode {
  const [amount, setAmount] = useState(5);
  return (
    <>
      <button type="button" onClick={() => setAmount(8)}>
        Next
      </button>
      <Nested amount={amount} />
    </>
  );
}

// Not a user's doing, so the browser's own event rather than userEvent.
function endAnimation(element: HTMLElement): void {
  act(() => {
    element.dispatchEvent(new AnimationEvent('animationend', { bubbles: true }));
  });
}

function figure(display: string): HTMLElement {
  return screen.getByText(display);
}

async function step(readings: readonly Reading[], better?: PulseBetter): Promise<void> {
  renderWithProviders(<Stepping readings={readings} better={better} />, NO_PROVIDERS);
  for (let index = 1; index < readings.length; index += 1) {
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
  }
}

describe('Pulse', () => {
  it('stays still on its first render', () => {
    renderWithProviders(<Pulse display="$5.00" measure={5} better="higher" scope="s" />, NO_PROVIDERS);

    expect(screen.getByText('$5.00')).not.toHaveAttribute('data-pulse');
  });

  it.each([
    ['higher', 5, 8, 'up'],
    ['higher', 8, 5, 'down'],
    ['lower', 5, 8, 'down'],
    ['lower', 8, 5, 'up'],
  ] as const)('reads a %s-is-better figure going %s to %s as %s', async (better, from, to, tone) => {
    await step(
      [
        { display: `#${from}`, measure: from, scope: 's' },
        { display: `#${to}`, measure: to, scope: 's' },
      ],
      better,
    );

    expect(figure(`#${to}`)).toHaveAttribute('data-pulse', tone);
  });

  it.each([
    ['appears', null, 3],
    ['vanishes', 3, null],
    ['keeps its measure under a new label', 3, 3],
  ] as const)('pulses new, with no direction, when a figure %s', async (_case, from, to) => {
    await step([
      { display: 'before', measure: from, scope: 's' },
      { display: 'after', measure: to, scope: 's' },
    ]);

    expect(figure('after')).toHaveAttribute('data-pulse', 'new');
  });

  it('holds still when the scope changes with the figure, as a new selection does', async () => {
    await step([
      { display: '$5.00', measure: 5, scope: 'week-1' },
      { display: '$8.00', measure: 8, scope: 'week-2' },
    ]);

    expect(figure('$8.00')).not.toHaveAttribute('data-pulse');
  });

  it('never pulses without a scope', async () => {
    await step([
      { display: '$5.00', measure: 5, scope: undefined },
      { display: '$8.00', measure: 8, scope: undefined },
    ]);

    expect(figure('$8.00')).not.toHaveAttribute('data-pulse');
  });

  it('pulses a refresh that follows a scope change', async () => {
    await step([
      { display: '$5.00', measure: 5, scope: 'week-1' },
      { display: '$8.00', measure: 8, scope: 'week-2' },
      { display: '$9.00', measure: 9, scope: 'week-2' },
    ]);

    expect(figure('$9.00')).toHaveAttribute('data-pulse', 'up');
  });

  it('keeps pulsing through a render that leaves the figure alone', async () => {
    await step([
      { display: '$5.00', measure: 5, scope: 's' },
      { display: '$8.00', measure: 8, scope: 's' },
      { display: '$8.00', measure: 8, scope: 's' },
    ]);

    expect(figure('$8.00')).toHaveAttribute('data-pulse', 'up');
  });

  it('clears once its own animation ends, not when a nested one does', async () => {
    renderWithProviders(<SteppingNested />, NO_PROVIDERS);
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    const inner = screen.getByText('$8');
    const outer = inner.parentElement ?? document.body;
    expect(outer).toHaveAttribute('data-pulse', 'up');

    endAnimation(inner);
    expect(inner).not.toHaveAttribute('data-pulse');
    expect(outer).toHaveAttribute('data-pulse', 'up');

    endAnimation(outer);
    expect(outer).not.toHaveAttribute('data-pulse');
  });

  it('flashes behind the figure from its pulse attribute alone', () => {
    renderWithProviders(<PulseDollars amount={12.5} scope="s" />, NO_PROVIDERS);

    expect(screen.getByText('+$12.50')).toHaveClass(
      'data-[pulse=up]:animate-pulse-up',
      'data-[pulse=down]:animate-pulse-down',
      'data-[pulse=new]:animate-pulse-new',
    );
  });
});
