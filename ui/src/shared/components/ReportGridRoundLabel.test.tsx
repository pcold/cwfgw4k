import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ReportGridRoundLabel from './ReportGridRoundLabel';

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

describe('ReportGridRoundLabel', () => {
  it('tags a late-row round under its number', () => {
    renderInRow(<ReportGridRoundLabel round={6} lateRow />);

    expect(screen.getByRole('cell')).toHaveTextContent(/^6Late row$/);
  });

  it('tags no other round', () => {
    renderInRow(<ReportGridRoundLabel round={2} lateRow={false} />);

    expect(screen.getByRole('cell')).toHaveTextContent(/^2$/);
  });

  it('sets what it is given under the tag', () => {
    renderInRow(
      <ReportGridRoundLabel round={6} lateRow>
        ($20.00)
      </ReportGridRoundLabel>,
    );

    expect(screen.getByRole('cell')).toHaveTextContent(/^6Late row\(\$20\.00\)$/);
  });

  it('stays sticky on the canvas, so the team cells scroll under it', () => {
    renderInRow(<ReportGridRoundLabel round={1} lateRow={false} />);

    expect(screen.getByRole('cell')).toHaveClass('sticky', 'left-0', 'bg-canvas');
  });
});
