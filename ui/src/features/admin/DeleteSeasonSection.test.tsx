import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeSeason } from '@/shared/test/fixtures';
import OpenQuery from '@/shared/test/OpenQuery';
import DeleteSeasonSection from './DeleteSeasonSection';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const deleteMock = vi.fn<typeof api.deleteSeason>();

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
    seasonsMock.mockResolvedValue([makeSeason()]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('deletes the selected season after confirm', async () => {
    deleteMock.mockResolvedValue(undefined);
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

  it('refetches an open data health check after the season is deleted', async () => {
    const fetcher = vi.fn().mockResolvedValue({});
    deleteMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(
      <>
        <DeleteSeasonSection />
        <OpenQuery queryKey={queryKeys.dataHealth.root} fetcher={fetcher} />
      </>,
      { withLeagueSeasonProvider: false },
    );

    await screen.findByRole('option', { name: /^2026 Spring$/ });
    expect(fetcher).toHaveBeenCalledTimes(1);
    await user.selectOptions(screen.getByLabelText(/Season to delete/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Delete Season/i }));

    await waitFor(() => {
      expect(fetcher).toHaveBeenCalledTimes(2);
    });
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

  it('deletes a season in the league the admin picks, not only the first', async () => {
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'lg-2', name: 'Beta', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockImplementation((leagueId) =>
      Promise.resolve(
        leagueId === 'lg-2'
          ? [makeSeason({ id: 'sn-beta', leagueId: 'lg-2', name: 'Fall' })]
          : [makeSeason()],
      ),
    );
    deleteMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<DeleteSeasonSection />, { withLeagueSeasonProvider: false });

    await screen.findByRole('option', { name: 'Beta' });
    await user.selectOptions(screen.getByLabelText('League'), 'lg-2');
    await screen.findByRole('option', { name: /^2026 Fall$/ });
    expect(screen.queryByRole('option', { name: /^2026 Spring$/ })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/Season to delete/i), 'sn-beta');
    await user.click(screen.getByRole('button', { name: /Delete Season/i }));

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith('sn-beta');
    });
    expect(seasonsMock).toHaveBeenCalledWith('lg-2');
  });

  it('names the season picker with a visible label', () => {
    renderWithProviders(<DeleteSeasonSection />, { withLeagueSeasonProvider: false });
    expect(screen.getByText('Season to delete')).toBeInTheDocument();
  });
});
