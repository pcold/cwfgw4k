import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import MoreSheet from './MoreSheet';

function renderSheet(initialPath = '/scoreboard', onClose = vi.fn()): HTMLElement {
  renderWithProviders(<MoreSheet id="more-sheet" onClose={onClose} />, {
    initialPath,
    withLeagueSeasonProvider: false,
  });
  return screen.getByRole('dialog', { name: 'More' });
}

describe('MoreSheet', () => {
  it('lists Rosters, Bets and Rules, then Admin past a divider', () => {
    const sheet = renderSheet();

    const rows = within(sheet).getAllByRole('link');
    expect(rows.map((row) => [row.textContent, row.getAttribute('href')])).toEqual([
      ['Rosters', '/rosters'],
      ['Bets', '/late-row-bets'],
      ['Rules', '/rules'],
      ['Admin', '/admin'],
    ]);
    const divider = within(sheet).getByRole('separator');
    expect(divider.compareDocumentPosition(rows[2] as HTMLElement)).toBe(Node.DOCUMENT_POSITION_PRECEDING);
    expect(divider.compareDocumentPosition(rows[3] as HTMLElement)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('gives every row the tap-target height across the sheet', () => {
    const sheet = renderSheet();

    for (const row of within(sheet).getAllByRole('link')) {
      expect(row).toHaveClass(TAP_TARGET_CLASS, 'flex', 'items-center');
    }
  });

  it.each([
    ['/rosters', 'Rosters'],
    ['/late-row-bets', 'Bets'],
    ['/rules', 'Rules'],
    ['/admin', 'Admin'],
  ])('marks only the row for %s as the current page', (path, label) => {
    const sheet = renderSheet(path);

    const current = within(sheet)
      .getAllByRole('link')
      .filter((row) => row.getAttribute('aria-current') === 'page');
    expect(current.map((row) => row.textContent)).toEqual([label]);
  });

  it('carries the id the More tab controls', () => {
    const sheet = renderSheet();

    expect(sheet).toContainElement(document.getElementById('more-sheet'));
  });

  it('closes from its Close control', async () => {
    const onClose = vi.fn();
    renderSheet('/scoreboard', onClose);

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledOnce();
  });
});
