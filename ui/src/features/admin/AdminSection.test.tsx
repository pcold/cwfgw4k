import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import AdminSection from './AdminSection';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('AdminSection', () => {
  it('heads its card with the title, then the description, then the section body', () => {
    renderWithProviders(
      <AdminSection title="Delete Season" description="This cannot be undone.">
        <button type="button">Delete</button>
      </AdminSection>,
      NO_PROVIDERS,
    );

    const heading = screen.getByRole('heading', { level: 3, name: 'Delete Season' });
    const card = heading.parentElement;
    expect(card).toHaveClass('bg-surface-card', 'rounded-lg', 'p-6');
    expect(Array.from(card?.children ?? []).map((child) => child.textContent)).toEqual([
      'Delete Season',
      'This cannot be undone.',
      'Delete',
    ]);
    expect(screen.getByText('This cannot be undone.')).toHaveClass('text-xs', 'text-ink-muted');
  });

  it('sets the body straight under the title when there is no description', () => {
    renderWithProviders(
      <AdminSection title="Create League">
        <button type="button">Create</button>
      </AdminSection>,
      NO_PROVIDERS,
    );

    const card = screen.getByRole('heading', { name: 'Create League' }).parentElement;
    expect(Array.from(card?.children ?? []).map((child) => child.tagName)).toEqual(['H3', 'BUTTON']);
  });
});
