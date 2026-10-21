import type { FormEvent } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import FormModal from './FormModal';
import { TAP_TARGET_CLASS } from './formControls';

interface Overrides {
  readonly isPending?: boolean;
  readonly serverError?: string;
}

function renderModal(overrides: Overrides = {}) {
  const onSubmit = vi.fn((event: FormEvent<HTMLFormElement>) => event.preventDefault());
  const onClose = vi.fn();
  renderWithProviders(
    <FormModal
      title="Admin Login"
      submitLabel="Log In"
      isPending={overrides.isPending ?? false}
      serverError={overrides.serverError}
      onSubmit={onSubmit}
      onClose={onClose}
    >
      <input aria-label="Field" />
    </FormModal>,
    { withLeagueSeasonProvider: false },
  );
  return { onSubmit, onClose };
}

describe('FormModal', () => {
  it('renders the title and the fields inside a dialog', () => {
    renderModal();

    expect(screen.getByRole('dialog', { name: 'Admin Login' })).toBeInTheDocument();
    expect(screen.getByLabelText('Field')).toBeInTheDocument();
  });

  it('submits through the footer button', async () => {
    const { onSubmit } = renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Log In' }));

    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('submits on Enter from a field', async () => {
    const { onSubmit } = renderModal();

    await userEvent.type(screen.getByLabelText('Field'), 'admin{Enter}');

    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('closes via both Cancel and the Close control', async () => {
    const { onClose } = renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('disables the submit button while a mutation is pending', () => {
    renderModal({ isPending: true });

    expect(screen.getByRole('button', { name: 'Log In' })).toBeDisabled();
  });

  it('shows the server error alert when provided', () => {
    renderModal({ serverError: 'Invalid credentials' });

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid credentials');
  });

  it('omits the error alert when there is no server error', () => {
    renderModal();

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('gives both footer buttons the tap-target height', () => {
    renderModal();

    expect(screen.getByRole('button', { name: 'Log In' })).toHaveClass(TAP_TARGET_CLASS);
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveClass(TAP_TARGET_CLASS);
  });
});
