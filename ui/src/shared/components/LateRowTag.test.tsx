import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import LateRowTag from './LateRowTag';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('LateRowTag', () => {
  it('outlines a late-row round in the muted ink, with no fill', () => {
    renderWithProviders(<LateRowTag variant="late-row" />, NO_PROVIDERS);

    const tag = screen.getByText('Late row');
    expect(tag).toHaveClass('border', 'border-hairline-strong', 'text-ink-muted', 'uppercase', 'font-display');
    expect(tag.className).not.toMatch(/\bbg-/);
  });

  it('fills the round a team leads in gold', () => {
    renderWithProviders(<LateRowTag variant="late-row-leader" />, NO_PROVIDERS);

    expect(screen.getByText('Leads late row')).toHaveClass('bg-gold', 'border-gold', 'text-on-gold');
  });
});
