import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DeleteSeasonSection from './DeleteSeasonSection';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const deleteMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    deleteSeason: (id: string) => deleteMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

describe('DeleteSeasonSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    deleteMock.mockReset();
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockResolvedValue([
      {
        id: 'sn-1',
        leagueId: 'lg-1',
        name: 'Spring',
        seasonYear: 2026,
        seasonNumber: 1,
        status: 'active',
      },
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('deletes the selected season after confirm', async () => {
    deleteMock.mockResolvedValue({ message: 'Season deleted' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<DeleteSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to delete/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Delete Season/i }));

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith('sn-1');
    });
    expect(await screen.findByText(/Season deleted/)).toBeInTheDocument();
  });

  it('does nothing when the user cancels the confirm dialog', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const user = userEvent.setup();
    renderWithProviders(<DeleteSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to delete/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Delete Season/i }));

    expect(deleteMock).not.toHaveBeenCalled();
  });

  it('shows the API error message when the delete fails', async () => {
    deleteMock.mockRejectedValue(new Error('Season not found'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<DeleteSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to delete/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Delete Season/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/Season not found/);
  });
});
