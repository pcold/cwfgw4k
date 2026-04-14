import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ResetTournamentSection from './ResetTournamentSection';
import { renderWithProviders } from '../../test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const resetTournamentMock = vi.fn();

vi.mock('../../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    tournaments: (id: string) => tournamentsMock(id),
    resetTournament: (id: string) => resetTournamentMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

describe('ResetTournamentSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    resetTournamentMock.mockReset();
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
    tournamentsMock.mockResolvedValue([
      {
        id: 'tn-1',
        name: 'Sony Open',
        seasonId: 'sn-1',
        startDate: '2026-01-15',
        endDate: '2026-01-18',
        status: 'completed',
        payoutMultiplier: 1,
        week: '1',
        pgaTournamentId: null,
        courseName: null,
        purseAmount: null,
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'tn-2',
        name: 'Upcoming Open',
        seasonId: 'sn-1',
        startDate: '2026-05-15',
        endDate: '2026-05-18',
        status: 'upcoming',
        payoutMultiplier: 1,
        week: '20',
        pgaTournamentId: null,
        courseName: null,
        purseAmount: null,
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists only completed tournaments and resets on confirm', async () => {
    resetTournamentMock.mockResolvedValue({ message: 'Reset complete' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });
    expect(screen.queryByRole('option', { name: /Upcoming Open/ })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    await user.click(screen.getByRole('button', { name: /Reset Results/i }));

    await waitFor(() => {
      expect(resetTournamentMock).toHaveBeenCalledWith('tn-1');
    });
    expect(await screen.findByText(/Reset complete/)).toBeInTheDocument();
  });

  it('does not call the API when the user cancels the confirm dialog', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const user = userEvent.setup();
    renderWithProviders(<ResetTournamentSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Wk 1 — Sony Open/ })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/Completed tournament/i), 'tn-1');
    await user.click(screen.getByRole('button', { name: /Reset Results/i }));

    expect(resetTournamentMock).not.toHaveBeenCalled();
  });
});
