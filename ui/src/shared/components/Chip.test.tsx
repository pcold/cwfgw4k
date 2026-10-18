import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import Chip from './Chip';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('Chip', () => {
  it('fills a neutral chip with the raised surface under the body ink', () => {
    renderWithProviders(<Chip variant="neutral">Aces</Chip>, NO_PROVIDERS);

    const chip = screen.getByText('Aces');
    expect(chip).toHaveClass('bg-surface-raised', 'text-ink-body', 'border-transparent');
    expect(chip).not.toHaveClass('border-hairline-strong');
  });

  it('outlines an outlined chip on whatever it sits on, with no fill of its own', () => {
    renderWithProviders(<Chip variant="outlined">Undrafted</Chip>, NO_PROVIDERS);

    const chip = screen.getByText('Undrafted');
    expect(chip).toHaveClass('border-hairline-strong', 'text-ink-muted');
    expect(chip.className).not.toMatch(/(?<![\w-])bg-/);
  });

  it.each(['neutral', 'outlined'] as const)('sets a %s chip in the one chip shape', (variant) => {
    renderWithProviders(<Chip variant={variant}>Label</Chip>, NO_PROVIDERS);

    expect(screen.getByText('Label')).toHaveClass(
      'border',
      'rounded',
      'px-1.5',
      'py-0.5',
      'text-xs',
      'uppercase',
      'tracking-wider',
      'whitespace-nowrap',
    );
  });
});
