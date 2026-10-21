import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { useHeaderSlot } from './HeaderSlot';

function SlotProbe(): string {
  const slot = useHeaderSlot();
  return slot === null ? 'no slot' : `slot ${slot.id}`;
}

describe('useHeaderSlot', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('reads the element the provider holds', () => {
    const slot = document.createElement('div');
    slot.id = 'nav-slot';

    renderWithProviders(<SlotProbe />, { withLeagueSeasonProvider: false, headerSlot: slot });

    expect(screen.getByText('slot nav-slot')).toBeInTheDocument();
  });

  it('reads null from a provider whose slot has not mounted', () => {
    renderWithProviders(<SlotProbe />, { withLeagueSeasonProvider: false });

    expect(screen.getByText('no slot')).toBeInTheDocument();
  });

  it('throws outside a provider rather than read as a missing slot', () => {
    // React reports the render error to console.error before rethrowing it.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    expect(() =>
      renderWithProviders(<SlotProbe />, { withLeagueSeasonProvider: false, withHeaderSlotProvider: false }),
    ).toThrow('useHeaderSlot must be used inside a HeaderSlotProvider');
  });
});
