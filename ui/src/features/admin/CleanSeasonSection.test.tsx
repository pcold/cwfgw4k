import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CleanSeasonSection from './CleanSeasonSection';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const cleanMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    cleanSeasonResults: (id: string) => cleanMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

describe('CleanSeasonSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    cleanMock.mockReset();
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

  it('cleans the selected season after confirm', async () => {
    cleanMock.mockResolvedValue({
      scoresDeleted: 12,
      resultsDeleted: 3,
      standingsDeleted: 5,
      tournamentsReset: 2,
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<CleanSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to clean/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Clean Results/i }));

    await waitFor(() => {
      expect(cleanMock).toHaveBeenCalledWith('sn-1');
    });
    expect(
      await screen.findByText(
        /Cleaned 12 scores, 3 results, 5 standings; reset 2 tournaments\./,
      ),
    ).toBeInTheDocument();
  });

  it('does nothing when the user cancels the confirm dialog', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const user = userEvent.setup();
    renderWithProviders(<CleanSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /^2026 Spring$/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Season to clean/i), 'sn-1');
    await user.click(screen.getByRole('button', { name: /Clean Results/i }));

    expect(cleanMock).not.toHaveBeenCalled();
  });
});
