import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import MoneyCell from './MoneyCell';

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

describe('MoneyCell', () => {
  it.each([
    ['+$1,082', 'bg-money-up-fill', ['bg-money-down-fill', 'bg-money-even-fill']],
    ['\u2212$35', 'bg-money-down-fill', ['bg-money-up-fill', 'bg-money-even-fill']],
    ['$0', 'bg-money-even-fill', ['bg-money-up-fill', 'bg-money-down-fill']],
  ] as const)('fills %s with the tone of its sign, in the large white figure', (display, tone, otherTones) => {
    renderInRow(<MoneyCell display={display} />);

    const cell = screen.getByRole('cell', { name: display });
    expect(cell).toHaveClass(tone, 'text-on-fill', 'rounded-r', 'font-display', 'font-bold', 'text-xl');
    expect(cell).not.toHaveClass(...otherTones);
  });

  it('keeps the card-row shape by default, its classes unchanged for the standings and scoreboard', () => {
    renderInRow(<MoneyCell display="+$1,082" />);

    expect(screen.getByRole('cell', { name: '+$1,082' }).className).toBe(
      'px-3 py-2 rounded-r text-right text-on-fill font-display font-bold text-xl tabular-nums bg-money-up-fill',
    );
  });

  it('squares a standalone box, in the same padding, fill and figure', () => {
    renderInRow(<MoneyCell display={'\u2212$35'} shape="standalone" />);

    const cell = screen.getByRole('cell', { name: '\u2212$35' });
    expect(cell).toHaveClass('px-3', 'py-2', 'bg-money-down-fill', 'text-on-fill', 'font-display', 'font-bold', 'text-xl');
    expect(cell.className).not.toMatch(/rounded/);
  });

  it.each([
    ['+$1,082', 'bg-money-up-fill'],
    ['\u2212$35', 'bg-money-down-fill'],
    ['$0', 'bg-money-even-fill'],
  ] as const)('sets a card-end box for %s as a div rounded on the right, in the same fill and figure', (display, tone) => {
    renderWithProviders(<MoneyCell display={display} shape="card-end" />, NO_PROVIDERS);

    const box = screen.getByText(display);
    expect(box.tagName).toBe('DIV');
    expect(box).toHaveClass(tone, 'rounded-r', 'min-w-24', 'shrink-0', 'text-on-fill', 'font-display', 'font-bold', 'text-xl');
  });
});
