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
  ] as const)('fills position %i with its tone, in the condensed bold number', (position, tone, otherTones) => {
    renderInRow(<PositionCell position={position} />);

    const cell = screen.getByRole('cell', { name: String(position) });
    expect(cell).toHaveClass(...tone, 'rounded-l', 'font-display', 'font-bold');
    expect(cell).not.toHaveClass(...otherTones);
  });

  it('keeps the row-start cell by default, its classes unchanged for the standings and scoreboard tables', () => {
    renderInRow(<PositionCell position={1} />);

    expect(screen.getByRole('cell', { name: '1' }).className).toBe(
      'px-3 py-2 rounded-l text-center font-display font-bold text-lg tabular-nums bg-gold text-on-gold',
    );
  });

  it.each([
    [1, 'bg-gold', 'bg-position-fill'],
    [2, 'bg-position-fill', 'bg-gold'],
  ] as const)('sets a card-start box for position %i as a narrow div in the same tone and figure', (position, tone, otherTone) => {
    renderWithProviders(<PositionCell position={position} shape="card-start" />, NO_PROVIDERS);

    const box = screen.getByText(String(position));
    expect(box.tagName).toBe('DIV');
    expect(box).toHaveClass(tone, 'w-9.5', 'shrink-0', 'rounded-l', 'font-display', 'font-bold', 'text-lg');
    expect(box).not.toHaveClass(otherTone);
  });
});
