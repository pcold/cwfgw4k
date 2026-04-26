import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CreateSeasonSection from './CreateSeasonSection';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const createSeasonMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    createSeason: (input: unknown) => createSeasonMock(input),
  },
  ApiError: class ApiError extends Error {},
}));

describe('CreateSeasonSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    createSeasonMock.mockReset();
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'lg-2', name: 'Beta', createdAt: '2026-01-01T00:00:00Z' },
    ]);
  });

  it('submits the season with the current rules', async () => {
    createSeasonMock.mockResolvedValue({
      id: 'sn-5',
      leagueId: 'lg-1',
      name: 'Spring',
      seasonYear: 2030,
      seasonNumber: 1,
      status: 'active',
    });

    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    // Wait for leagues to populate the select so leagueId is set.
    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });

    const yearInput = screen.getByLabelText(/Year/i) as HTMLInputElement;
    await user.clear(yearInput);
    await user.type(yearInput, '2030');
    await user.type(screen.getByLabelText(/Season Name/i), 'Spring');

    await user.click(screen.getByRole('button', { name: /Create Season/i }));

    await waitFor(() => {
      expect(createSeasonMock).toHaveBeenCalledTimes(1);
    });
    expect(createSeasonMock).toHaveBeenCalledWith({
      leagueId: 'lg-1',
      name: 'Spring',
      seasonYear: 2030,
      rules: {
        payouts: [18, 12, 10, 8, 7, 6, 5, 4, 3, 2],
        tieFloor: 1,
        sideBetRounds: [5, 6, 7, 8],
        sideBetAmount: 15,
      },
    });
    expect(await screen.findByText(/Season "Spring" created/i)).toBeInTheDocument();
  });

  it('lets the user add and remove payout places', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });

    expect(screen.getAllByLabelText(/^Payout \d+$/)).toHaveLength(10);

    await user.click(screen.getByRole('button', { name: /\+ Add place/i }));
    expect(screen.getAllByLabelText(/^Payout \d+$/)).toHaveLength(11);

    await user.click(screen.getByRole('button', { name: /- Remove last/i }));
    expect(screen.getAllByLabelText(/^Payout \d+$/)).toHaveLength(10);
  });

  it('disables submit when the season name is blank', async () => {
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });
    expect(screen.getByRole('button', { name: /Create Season/i })).toBeDisabled();
  });
});
