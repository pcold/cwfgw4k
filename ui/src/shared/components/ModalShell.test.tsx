import { useState } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ModalShell, { MODAL_SCROLL_LOCK_CLASS } from './ModalShell';

/**
 * Mounts the shell the way the app does — from a trigger, with page content behind it — so the
 * focus obligations can be asserted against a real opener and a real element to escape to.
 */
function ModalTrigger({ autoFocusField = false }: { readonly autoFocusField?: boolean }) {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setIsOpen(true)}>
        New season
      </button>
      <input aria-label="Behind the modal" />
      {isOpen && (
        <ModalShell title="New season" onClose={() => setIsOpen(false)}>
          {/* eslint-disable-next-line jsx-a11y/no-autofocus -- the case under test. */}
          <input aria-label="Season name" autoFocus={autoFocusField} />
        </ModalShell>
      )}
    </>
  );
}

async function openModal(autoFocusField = false): Promise<HTMLElement> {
  renderWithProviders(<ModalTrigger autoFocusField={autoFocusField} />, { withLeagueSeasonProvider: false });
  const trigger = screen.getByRole('button', { name: 'New season' });
  await userEvent.click(trigger);
  return trigger;
}

function renderShell(onClose: () => void, dismissOnBackdrop: boolean): HTMLElement {
  renderWithProviders(
    <ModalShell title="Golfer" onClose={onClose} dismissOnBackdrop={dismissOnBackdrop}>
      <p>Body</p>
    </ModalShell>,
    { withLeagueSeasonProvider: false },
  );
  return screen.getByRole('dialog').parentElement as HTMLElement;
}

describe('ModalShell', () => {
  it('names the dialog after its title', () => {
    renderWithProviders(
      <ModalShell title="Manage player links" onClose={() => {}}>
        <p>Body</p>
      </ModalShell>,
      { withLeagueSeasonProvider: false },
    );

    // Queried by accessible name, so this fails if the generated id stops reaching aria-labelledby.
    expect(screen.getByRole('dialog', { name: 'Manage player links' })).toBeInTheDocument();
  });

  it('gives each open shell its own heading id', () => {
    renderWithProviders(
      <>
        <ModalShell title="First" onClose={() => {}}>
          <p>One</p>
        </ModalShell>
        <ModalShell title="Second" onClose={() => {}}>
          <p>Two</p>
        </ModalShell>
      </>,
      { withLeagueSeasonProvider: false },
    );

    expect(screen.getByRole('dialog', { name: 'First' })).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Second' })).toBeInTheDocument();
  });

  it('closes via the Close control', async () => {
    const onClose = vi.fn();
    renderShell(onClose, false);

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledOnce();
  });

  it('moves focus into the dialog when it opens', async () => {
    await openModal();

    expect(screen.getByRole('dialog')).toHaveFocus();
  });

  it('leaves focus on a field that took it on mount, and still returns it to the trigger', async () => {
    const trigger = await openModal(true);

    expect(screen.getByLabelText('Season name')).toHaveFocus();

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(trigger).toHaveFocus();
  });

  it('returns focus to the trigger when it closes', async () => {
    const trigger = await openModal();

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(trigger).toHaveFocus();
  });

  it('closes on Escape', async () => {
    const trigger = await openModal();

    await userEvent.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('cycles Tab within the dialog instead of reaching the page behind it', async () => {
    await openModal();
    const close = screen.getByRole('button', { name: 'Close' });
    const seasonName = screen.getByLabelText('Season name');

    await userEvent.tab();
    expect(close).toHaveFocus();
    await userEvent.tab();
    expect(seasonName).toHaveFocus();
    await userEvent.tab();
    expect(close).toHaveFocus();

    await userEvent.tab({ shift: true });
    expect(seasonName).toHaveFocus();

    expect(screen.getByLabelText('Behind the modal')).not.toHaveFocus();
  });

  it('pulls focus back into the dialog when it has drifted to the page behind', async () => {
    await openModal();
    const strayField = screen.getByLabelText('Behind the modal');
    await userEvent.click(strayField);
    expect(strayField).toHaveFocus();

    await userEvent.tab();

    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
  });

  it('suppresses page scroll only while it is open', async () => {
    expect(document.body).not.toHaveClass(MODAL_SCROLL_LOCK_CLASS);
    await openModal();
    expect(document.body).toHaveClass(MODAL_SCROLL_LOCK_CLASS);

    await userEvent.keyboard('{Escape}');

    expect(document.body).not.toHaveClass(MODAL_SCROLL_LOCK_CLASS);
  });

  it('closes on a backdrop click when asked to', async () => {
    const onClose = vi.fn();
    const backdrop = renderShell(onClose, true);

    await userEvent.click(screen.getByText('Body'));
    expect(onClose).not.toHaveBeenCalled();

    await userEvent.click(backdrop);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('ignores a backdrop click by default', async () => {
    const onClose = vi.fn();
    const backdrop = renderShell(onClose, false);

    await userEvent.click(backdrop);

    expect(onClose).not.toHaveBeenCalled();
  });
});
