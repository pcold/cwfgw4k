import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import MoreSheet from './MoreSheet';

// No leagues, so the team preference reads only what this device stored.
vi.mock('@/shared/api/client', () => ({
  api: { leagues: vi.fn().mockResolvedValue([]) },
}));

const MY_TEAM_KEY = 'cwfg.myTeam';

function renderSheet(initialPath = '/scoreboard', onClose = vi.fn()): HTMLElement {
  renderWithProviders(<MoreSheet id="more-sheet" onClose={onClose} />, {
    initialPath,
    withMyTeamProvider: true,
  });
  return screen.getByRole('dialog', { name: 'More' });
}

describe('MoreSheet', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

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

  it('puts the My team row last among the pages, above the divider', () => {
    const sheet = renderSheet();

    const myTeam = within(sheet).getByRole('button', { name: 'My team · Choose' });
    const rules = within(sheet).getByRole('link', { name: 'Rules' });
    expect(rules.compareDocumentPosition(myTeam)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(within(sheet).getByRole('separator').compareDocumentPosition(myTeam)).toBe(Node.DOCUMENT_POSITION_PRECEDING);
  });

  it('names the saved team on its My team row', () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const sheet = renderSheet();

    expect(within(sheet).getByRole('button', { name: 'My team: WOMBLE · Change' })).toBeInTheDocument();
  });

  it('offers a choice on the My team row to a visitor who is just browsing', () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"browsing"}');
    const sheet = renderSheet();

    expect(within(sheet).getByRole('button', { name: 'My team · Choose' })).toBeInTheDocument();
  });

  it('clears the saved team and closes the sheet from the My team row', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const onClose = vi.fn();
    renderSheet('/scoreboard', onClose);

    await userEvent.click(screen.getByRole('button', { name: 'My team: WOMBLE · Change' }));

    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('gives every row the tap-target height across the sheet', () => {
    const sheet = renderSheet();
    const rows = [...within(sheet).getAllByRole('link'), within(sheet).getByRole('button', { name: /^My team/ })];

    for (const row of rows) {
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
