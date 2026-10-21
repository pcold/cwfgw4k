import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import StatCard from './StatCard';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('StatCard', () => {
  it('prints the amount it is given, beside its label, on the card surface', () => {
    renderWithProviders(<StatCard label="Net" amount="+$1,082.40" />, NO_PROVIDERS);

    const label = screen.getByText('Net');
    expect(label.nextSibling).toHaveTextContent(/^\+\$1,082\.40$/);
    expect(label.parentElement).toHaveClass('bg-surface-card', 'rounded');
  });

  it.each([
    ['+$9', 'text-semantic-success', ['text-semantic-danger', 'text-ink-soft']],
    ['−$2.00', 'text-semantic-danger', ['text-semantic-success', 'text-ink-soft']],
    ['$0', 'text-ink-soft', ['text-semantic-success', 'text-semantic-danger']],
  ] as const)('tones %s by its sign', (amount, tone, otherTones) => {
    renderWithProviders(<StatCard label="Won" amount={amount} />, NO_PROVIDERS);

    const figure = screen.getByText(amount);
    expect(figure).toHaveClass(tone);
    expect(figure).not.toHaveClass(...otherTones);
  });
});
