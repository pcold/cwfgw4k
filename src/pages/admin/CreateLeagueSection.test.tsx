import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CreateLeagueSection from './CreateLeagueSection';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ApiError } from '@/api/client';

const createLeagueMock = vi.fn();

vi.mock('../../api/client', () => ({
  api: {
    createLeague: (name: string) => createLeagueMock(name),
  },
  ApiError: class ApiError extends Error {
    readonly status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

describe('CreateLeagueSection', () => {
  beforeEach(() => {
    createLeagueMock.mockReset();
  });

  it('creates a league and surfaces the success banner', async () => {
    createLeagueMock.mockResolvedValue({
      id: 'lg-99',
      name: 'New League',
      createdAt: '2026-01-01T00:00:00Z',
    });

    const user = userEvent.setup();
    renderWithProviders(<CreateLeagueSection />, { withLeagueSeasonProvider: false });

    await user.type(screen.getByLabelText(/New league name/i), '  New League  ');
    await user.click(screen.getByRole('button', { name: /Create League/i }));

    expect(createLeagueMock).toHaveBeenCalledWith('New League');
    expect(await screen.findByText(/Created "New League"/i)).toBeInTheDocument();
  });

  it('disables the submit button when the name is blank', () => {
    renderWithProviders(<CreateLeagueSection />, { withLeagueSeasonProvider: false });
    expect(screen.getByRole('button', { name: /Create League/i })).toBeDisabled();
  });

  it('surfaces a server error', async () => {
    createLeagueMock.mockRejectedValue(new ApiError(400, 'Name already exists'));

    const user = userEvent.setup();
    renderWithProviders(<CreateLeagueSection />, { withLeagueSeasonProvider: false });

    await user.type(screen.getByLabelText(/New league name/i), 'Dup');
    await user.click(screen.getByRole('button', { name: /Create League/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Name already exists/i);
  });
});
