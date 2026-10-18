import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import ReportGridHead from './ReportGridHead';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

function renderHead(): void {
  renderWithProviders(
    <table>
      <ReportGridHead
        cornerLabel="RD"
        teams={[
          { teamId: 't-1', number: 5, name: 'Aces' },
          { teamId: 't-2', number: 8, name: 'Birdies' },
        ]}
      />
    </table>,
    NO_PROVIDERS,
  );
}

describe('ReportGridHead', () => {
  it('sets each team in the header band, its number above its bold name', () => {
    renderHead();

    const [, aces, birdies] = screen.getAllByRole('columnheader');
    for (const [head, number, name] of [
      [aces, '5', 'Aces'],
      [birdies, '8', 'Birdies'],
    ] as const) {
      expect(head).toHaveClass('bg-header-band', 'text-on-fill');
      expect(head).toHaveTextContent(`${number}${name}`);
      expect(screen.getByText(name)).toHaveClass('font-bold');
    }
  });

  it('leaves the sticky corner on the canvas, out of the band', () => {
    renderHead();

    const corner = screen.getByRole('columnheader', { name: 'RD' });
    expect(corner).toHaveClass('bg-canvas', 'sticky', 'left-0');
    expect(corner).not.toHaveClass('bg-header-band');
  });

  it('sets its head in the shared column-header type', () => {
    renderHead();
    expectSharedColumnHeads(1);
  });
});
