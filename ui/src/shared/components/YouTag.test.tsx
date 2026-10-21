import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import YouTag from './YouTag';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('YouTag', () => {
  it('shows YOU but is announced as your team', () => {
    renderWithProviders(<YouTag />, NO_PROVIDERS);

    expect(screen.getByText('You')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('your team')).toHaveClass('sr-only');
  });

  // Styling alone, so the classes are what there is to assert.
  it('is a small uppercase navy tag with white text', () => {
    renderWithProviders(<YouTag />, NO_PROVIDERS);

    expect(screen.getByText('You').parentElement).toHaveClass(
      'bg-chrome',
      'text-on-chrome',
      'uppercase',
      'text-xs',
      'font-display',
    );
  });
});
