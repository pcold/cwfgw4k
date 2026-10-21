import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation, useNavigate } from 'react-router';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import TabBar from './TabBar';

// The More sheet reads the team preference, whose provider sits under the league and season one.
vi.mock('@/shared/api/client', () => ({
  api: { leagues: vi.fn().mockResolvedValue([]) },
}));

function CurrentPath() {
  return <output aria-label="Current path">{useLocation().pathname}</output>;
}

function BackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => void navigate(-1)}>
      Back
    </button>
  );
}

function renderTabBar(initialPath = '/scoreboard'): void {
  renderWithProviders(
    <>
      <CurrentPath />
      <BackButton />
      <TabBar />
    </>,
    { initialPath, withMyTeamProvider: true },
  );
}

const tabBar = (): HTMLElement => screen.getByRole('navigation', { name: 'Tabs' });

const tabs = (): HTMLElement[] => within(tabBar()).getAllByRole('listitem').map((item) => item.firstElementChild as HTMLElement);

const moreTab = (): HTMLElement => within(tabBar()).getByRole('button', { name: 'More' });

const indicatorOf = (tab: HTMLElement): Element | null => tab.querySelector('.bg-primary');

describe('TabBar', () => {
  it('shows Scores, Standings, Report, Players and More, in that order', () => {
    renderTabBar();

    expect(tabs().map((tab) => [tab.textContent, tab.getAttribute('href')])).toEqual([
      ['Scores', '/scoreboard'],
      ['Standings', '/rankings'],
      ['Report', '/report'],
      ['Players', '/player-rankings'],
      ['More', null],
    ]);
  });

  it('sits fixed at the foot of the viewport below lg only, clear of the home indicator', () => {
    renderTabBar();

    expect(tabBar()).toHaveClass('lg:hidden', 'fixed', 'bottom-0', 'z-40', 'bg-chrome', 'pb-[env(safe-area-inset-bottom)]');
    expect(within(tabBar()).getByRole('list')).toHaveClass('grid-cols-5', 'h-14');
  });

  it('gives every tab the tap-target height', () => {
    renderTabBar();

    for (const tab of tabs()) {
      expect(tab).toHaveClass(TAP_TARGET_CLASS);
    }
  });

  it.each([
    ['/scoreboard', 'Scores'],
    ['/rankings', 'Standings'],
    ['/report', 'Report'],
    ['/player-rankings', 'Players'],
  ])('marks only the %s tab as the current page', (path, label) => {
    renderTabBar(path);

    const current = tabs().filter((tab) => tab.getAttribute('aria-current') === 'page');
    expect(current.map((tab) => tab.textContent)).toEqual([label]);
  });

  it.each(['/rosters', '/late-row-bets', '/rules', '/admin'])('marks More as the current page on %s', (path) => {
    renderTabBar(path);

    const current = tabs().filter((tab) => tab.getAttribute('aria-current') === 'page');
    expect(current).toEqual([moreTab()]);
    expect(moreTab()).toHaveClass('font-bold', 'text-on-chrome');
    expect(indicatorOf(moreTab())).not.toBeNull();
  });

  it.each(['/', '/scoreboard'])('leaves More idle on %s', (path) => {
    renderTabBar(path);

    expect(moreTab()).not.toHaveAttribute('aria-current');
    expect(moreTab()).toHaveClass('text-on-chrome-tab-idle');
  });

  it('highlights no tab on /', () => {
    renderTabBar('/');

    expect(tabs().filter((tab) => tab.hasAttribute('aria-current'))).toEqual([]);
  });

  it('sets the active tab white and bold over the red bar, and every other tab in the idle ink without one', () => {
    renderTabBar('/rankings');

    for (const tab of tabs()) {
      if (tab.textContent === 'Standings') {
        expect(tab).toHaveClass('font-bold', 'text-on-chrome');
        expect(indicatorOf(tab)).toHaveAttribute('aria-hidden', 'true');
      } else {
        expect(tab).toHaveClass('font-medium', 'text-on-chrome-tab-idle');
        expect(tab).not.toHaveClass('font-bold');
        expect(indicatorOf(tab)).toBeNull();
      }
    }
  });

  it('opens the More sheet, which controls its content, from the More tab', async () => {
    const user = userEvent.setup();
    renderTabBar();
    expect(moreTab()).toHaveAttribute('aria-expanded', 'false');

    await user.click(moreTab());

    expect(moreTab()).toHaveAttribute('aria-expanded', 'true');
    const sheet = screen.getByRole('dialog', { name: 'More' });
    const controlled = document.getElementById(moreTab().getAttribute('aria-controls') ?? '');
    expect(sheet).toContainElement(controlled);
  });

  it('closes the sheet on Escape and returns focus to More', async () => {
    const user = userEvent.setup();
    renderTabBar();
    await user.click(moreTab());

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(moreTab()).toHaveAttribute('aria-expanded', 'false');
    expect(moreTab()).toHaveFocus();
  });

  it('closes the sheet on a tap on the scrim', async () => {
    const user = userEvent.setup();
    renderTabBar();
    await user.click(moreTab());

    await user.click(screen.getByRole('dialog').parentElement as HTMLElement);

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('follows a sheet row to its page and closes the sheet', async () => {
    const user = userEvent.setup();
    renderTabBar();
    await user.click(moreTab());

    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Bets' }));

    expect(screen.getByRole('status', { name: 'Current path' })).toHaveTextContent('/late-row-bets');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(moreTab()).toHaveAttribute('aria-current', 'page');
  });

  it('keeps the sheet closed on going back to the page it was opened from', async () => {
    const user = userEvent.setup();
    renderTabBar();
    await user.click(moreTab());
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Bets' }));

    await user.click(screen.getByRole('button', { name: 'Back' }));

    expect(screen.getByRole('status', { name: 'Current path' })).toHaveTextContent('/scoreboard');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(moreTab()).toHaveAttribute('aria-expanded', 'false');
  });

  it("closes the sheet on a tap on the current page's own row", async () => {
    const user = userEvent.setup();
    renderTabBar('/rules');
    await user.click(moreTab());

    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Rules' }));

    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
