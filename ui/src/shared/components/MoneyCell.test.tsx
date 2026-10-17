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
});
