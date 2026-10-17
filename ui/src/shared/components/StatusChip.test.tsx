import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import StatusChip, { statusChipVariant } from './StatusChip';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('statusChipVariant', () => {
  it('is null before the response has loaded', () => {
    expect(statusChipVariant(undefined)).toBeNull();
  });

  it('is live when the server overlaid the response, whatever the tournament status', () => {
    expect(statusChipVariant({ live: true, tournament: { status: 'completed' } })).toBe('live');
  });

  it('is live on a response that carries no tournament, such as the standings', () => {
    expect(statusChipVariant({ live: true })).toBe('live');
  });

  it('is final for a completed tournament the server did not overlay', () => {
    expect(statusChipVariant({ live: false, tournament: { status: 'completed' } })).toBe('final');
  });

  it('is upcoming for an upcoming tournament', () => {
    expect(statusChipVariant({ live: null, tournament: { status: 'upcoming' } })).toBe('upcoming');
  });

  it('is null for an in-progress tournament shown without the overlay', () => {
    expect(statusChipVariant({ live: false, tournament: { status: 'in_progress' } })).toBeNull();
  });

  it('is null for a season report, whose tournament has no status', () => {
    expect(statusChipVariant({ live: null, tournament: { status: null } })).toBeNull();
  });

  it('is null for a response with neither a live overlay nor a tournament', () => {
    expect(statusChipVariant({ live: false })).toBeNull();
  });
});

describe('StatusChip', () => {
  it.each([
    ['live', 'Live', ['bg-primary', 'text-on-primary']],
    ['final', 'Final', ['bg-chrome', 'text-on-chrome']],
    ['upcoming', 'Upcoming', ['border-ink-strong', 'text-ink-strong']],
  ] as const)('names the %s state in its text, in its own colours', (variant, label, classes) => {
    renderWithProviders(<StatusChip variant={variant} />, NO_PROVIDERS);

    expect(screen.getByText(label)).toHaveClass(...classes);
  });

  it('renders nothing without a variant', () => {
    const { container } = renderWithProviders(<StatusChip variant={null} />, NO_PROVIDERS);

    expect(container).toBeEmptyDOMElement();
  });
});
