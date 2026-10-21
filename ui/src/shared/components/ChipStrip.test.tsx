import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from './formControls';
import ChipStrip from './ChipStrip';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const CHIPS = [
  { id: 'round-5', label: 'Rd 5' },
  { id: 'round-6', label: 'Rd 6' },
  { id: 'totals', label: 'Totals' },
];

function strip(): HTMLElement {
  return screen.getByRole('group', { name: 'Late row round' });
}

describe('ChipStrip', () => {
  it('names the group by its label and presses the selected chip alone, in the chrome fill', () => {
    renderWithProviders(
      <ChipStrip groupLabel="Late row round" chips={CHIPS} selectedId="totals" onSelect={vi.fn()} />,
      NO_PROVIDERS,
    );

    const pressed = within(strip())
      .getAllByRole('button')
      .map((chip) => [chip.textContent, chip.getAttribute('aria-pressed')]);
    expect(pressed).toEqual([
      ['Rd 5', 'false'],
      ['Rd 6', 'false'],
      ['Totals', 'true'],
    ]);
    expect(within(strip()).getByRole('button', { name: 'Totals' })).toHaveClass('bg-chrome', 'text-on-chrome');
    expect(within(strip()).getByRole('button', { name: 'Rd 5' })).toHaveClass(
      'bg-surface-card',
      'border-hairline-strong',
      'text-ink',
    );
  });

  it("reports the tapped chip's id", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ChipStrip groupLabel="Late row round" chips={CHIPS} selectedId="round-5" onSelect={onSelect} />,
      NO_PROVIDERS,
    );

    await user.click(within(strip()).getByRole('button', { name: 'Rd 6' }));

    expect(onSelect).toHaveBeenCalledWith('round-6');
  });

  it('presses no chip for an id none of them has', () => {
    renderWithProviders(
      <ChipStrip groupLabel="Late row round" chips={CHIPS} selectedId="round-9" onSelect={vi.fn()} />,
      NO_PROVIDERS,
    );

    for (const chip of within(strip()).getAllByRole('button')) {
      expect(chip).toHaveAttribute('aria-pressed', 'false');
    }
  });

  it('scrolls sideways, its chips at tap-target height in the uppercase display type, never wrapping', () => {
    renderWithProviders(
      <ChipStrip groupLabel="Late row round" chips={CHIPS} selectedId="round-5" onSelect={vi.fn()} />,
      NO_PROVIDERS,
    );

    expect(strip()).toHaveClass('flex', 'overflow-x-auto');
    for (const chip of within(strip()).getAllByRole('button')) {
      expect(chip).toHaveClass(TAP_TARGET_CLASS, 'shrink-0', 'whitespace-nowrap', 'font-display', 'uppercase');
    }
  });

  it('fades the right edge over as much end padding, so the last chip scrolls clear of the fade', () => {
    renderWithProviders(
      <ChipStrip groupLabel="Late row round" chips={CHIPS} selectedId="round-5" onSelect={vi.fn()} />,
      NO_PROVIDERS,
    );

    expect(strip()).toHaveClass('mask-r-from-[calc(100%-2rem)]', 'pr-8');
  });
});
