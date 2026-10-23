import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/shared/api/client';
import type { RosterTeam } from '@/shared/api/types';
import { makeLeague, makeSeason } from '@/shared/test/fixtures';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from '@/shared/components/formControls';
import MoreSheet from './MoreSheet';

vi.mock('@/shared/api/client', () => ({
  api: { leagues: vi.fn(), seasons: vi.fn(), rosters: vi.fn() },
}));

const MY_TEAM_KEY = 'cwfg.myTeam';

const WOMBLE: RosterTeam = { teamId: 'tm-womble', teamNumber: null, teamName: 'Womble', picks: [] };

function mockNewestTeams(newestTeams: readonly RosterTeam[]): void {
  vi.mocked(api.leagues).mockResolvedValue([makeLeague()]);
  vi.mocked(api.seasons).mockResolvedValue([makeSeason()]);
  vi.mocked(api.rosters).mockResolvedValue([...newestTeams]);
}

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
    mockNewestTeams([WOMBLE]);
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

  it('puts the My team row last among the pages, above the divider', async () => {
    const sheet = renderSheet();

    const myTeam = await within(sheet).findByRole('button', { name: 'My team · Choose' });
    const rules = within(sheet).getByRole('link', { name: 'Rules' });
    expect(rules.compareDocumentPosition(myTeam)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(within(sheet).getByRole('separator').compareDocumentPosition(myTeam)).toBe(Node.DOCUMENT_POSITION_PRECEDING);
  });

  it('names the saved team on its My team row', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const sheet = renderSheet();

    expect(await within(sheet).findByRole('button', { name: 'My team: WOMBLE · Change' })).toHaveAttribute(
      'aria-haspopup',
      'dialog',
    );
  });

  it('offers a choice on the My team row to a visitor who is just browsing', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"browsing"}');
    const sheet = renderSheet();

    expect(await within(sheet).findByRole('button', { name: 'My team · Choose' })).toBeInTheDocument();
  });

  it('clears the saved team and closes the sheet from the My team row', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const onClose = vi.fn();
    renderSheet('/scoreboard', onClose);

    await userEvent.click(await screen.findByRole('button', { name: 'My team: WOMBLE · Change' }));

    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
    expect(onClose).toHaveBeenCalledOnce();
  });

  // Change clears the choice and the picker opens; where the picker can't, the row would only clear it.
  it.each([
    ['the newest season has no teams yet', '/scoreboard', []],
    ['on Admin', '/admin', [WOMBLE]],
  ])('offers no My team row when %s, and keeps the saved team', async (_case, path, newestTeams) => {
    mockNewestTeams(newestTeams);
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    const sheet = renderSheet(path);

    await waitFor(() => {
      expect(api.rosters).toHaveBeenCalledOnce();
    });
    await act(async () => {
      await vi.mocked(api.rosters).mock.results.at(-1)?.value;
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(within(sheet).queryByRole('button', { name: /^My team/ })).not.toBeInTheDocument();
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBe('{"kind":"team","teamName":"WOMBLE"}');
  });

  it('gives every row the tap-target height across the sheet', async () => {
    const sheet = renderSheet();
    const rows = [
      ...within(sheet).getAllByRole('link'),
      await within(sheet).findByRole('button', { name: /^My team/ }),
    ];

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
