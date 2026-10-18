import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ReportGridCell from './ReportGridCell';

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

describe('ReportGridCell', () => {
  it('is a white cell with no border by default', () => {
    renderInRow(<ReportGridCell>Scheffler</ReportGridCell>);

    const cell = screen.getByRole('cell', { name: 'Scheffler' });
    expect(cell).toHaveClass('bg-surface-card');
    expect(cell.className).not.toMatch(/\bborder\b|border-/);
  });

  it('fills a late-row leader pale gold, edged in gold on the left', () => {
    renderInRow(<ReportGridCell fill="late-row-leader">Fox</ReportGridCell>);

    const cell = screen.getByRole('cell', { name: 'Fox' });
    expect(cell).toHaveClass('bg-late-row-leader', 'border-l-4', 'border-gold');
    expect(cell).not.toHaveClass('bg-surface-card');
  });
});
