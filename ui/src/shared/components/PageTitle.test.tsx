import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PageTitle from './PageTitle';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('PageTitle', () => {
  it('renders the title as the page h2 in the italic condensed navy face', () => {
    renderWithProviders(<PageTitle title="Team Standings" />, NO_PROVIDERS);

    expect(screen.getByRole('heading', { level: 2, name: 'Team Standings' })).toHaveClass(
      'font-display',
      'italic',
      'font-extrabold',
      'uppercase',
      'text-ink-strong',
    );
  });

  it('puts the subtitle and the actions inside the same header as the title', () => {
    renderWithProviders(
      <PageTitle
        title="Team Standings"
        subtitle="3 weeks played"
        actions={<button type="button">Pick a season</button>}
      />,
      NO_PROVIDERS,
    );

    const header = screen.getByRole('banner');
    expect(within(header).getByRole('heading', { name: 'Team Standings' })).toBeInTheDocument();
    expect(within(header).getByText('3 weeks played')).toBeInTheDocument();
    expect(within(header).getByRole('button', { name: 'Pick a season' })).toBeInTheDocument();
  });

  it('leaves out the subtitle line when there is none', () => {
    renderWithProviders(<PageTitle title="Season Rules" />, NO_PROVIDERS);

    expect(screen.getByRole('banner')).toHaveTextContent(/^Season Rules$/);
  });
});
