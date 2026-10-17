import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PositionCell from './PositionCell';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

function renderInRow(cell: ReactElement): void {
  renderWithProviders(
    <table>
      <tbody>
        <tr>{cell}</tr>
      </tbody>
    </table>,
    NO_PROVIDERS,
  );
}

describe('PositionCell', () => {
  it.each([
    [1, ['bg-gold', 'text-on-gold'], ['bg-position-fill', 'text-on-fill']],
    [2, ['bg-position-fill', 'text-on-fill'], ['bg-gold', 'text-on-gold']],
    [13, ['bg-position-fill', 'text-on-fill'], ['bg-gold', 'text-on-gold']],
  ] as const)('fills position %i with its tone, in the condensed bold number', (position, tone, otherTone) => {
    renderInRow(<PositionCell position={position} />);

    const cell = screen.getByRole('cell', { name: String(position) });
    expect(cell).toHaveClass(...tone, 'rounded-l', 'font-display', 'font-bold');
    expect(cell).not.toHaveClass(...otherTone);
  });
});
