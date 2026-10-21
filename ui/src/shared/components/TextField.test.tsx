import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import TextField from './TextField';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('TextField', () => {
  it('shows an inline error and links it to the input for assistive tech', () => {
    renderWithProviders(
      <TextField id="name" label="Name" value="" error="Name is required" onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Name');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Name is required');
    expect(screen.getByRole('alert')).toHaveTextContent('Name is required');
  });

  it('leaves the input unmarked when there is no error', () => {
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Name');
    expect(input).toHaveAttribute('aria-invalid', 'false');
    expect(input).not.toHaveAttribute('aria-describedby');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('reports each keystroke as the new value through onChange', async () => {
    const onChange = vi.fn();
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} onChange={onChange} />,
      NO_PROVIDERS,
    );

    await userEvent.type(screen.getByLabelText('Name'), 'a');

    expect(onChange).toHaveBeenLastCalledWith('a');
  });

  it('masks a password field', () => {
    renderWithProviders(
      <TextField id="pw" label="Password" type="password" value="hunter2" error={undefined} onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
  });

  it('takes focus on mount only when asked', () => {
    renderWithProviders(
      <>
        <TextField id="user" label="Username" value="" error={undefined} initialFocus onChange={vi.fn()} />
        <TextField id="pw" label="Password" value="" error={undefined} onChange={vi.fn()} />
      </>,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('Username')).toHaveFocus();
  });

  it('takes no keystrokes while disabled', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} disabled onChange={onChange} />,
      NO_PROVIDERS,
    );

    await user.type(screen.getByLabelText('Name'), 'x');

    expect(screen.getByLabelText('Name')).toBeDisabled();
    expect(onChange).not.toHaveBeenCalled();
  });
});
