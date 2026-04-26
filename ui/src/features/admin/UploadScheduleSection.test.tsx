import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UploadScheduleSection from './UploadScheduleSection';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const importSeasonScheduleMock = vi.fn();
const updateTournamentMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    importSeasonSchedule: (input: unknown) => importSeasonScheduleMock(input),
    updateTournament: (id: string, body: unknown) => updateTournamentMock(id, body),
  },
  ApiError: class ApiError extends Error {},
}));

function buildTournament(overrides: { id: string; name: string; week: string; payoutMultiplier?: number }) {
  return {
    id: overrides.id,
    pgaTournamentId: `pga-${overrides.id}`,
    name: overrides.name,
    seasonId: 'sn-1',
    startDate: '2026-04-09',
    endDate: '2026-04-12',
    courseName: null,
    status: 'upcoming' as const,
    purseAmount: null,
    payoutMultiplier: overrides.payoutMultiplier ?? 1,
    week: overrides.week,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

describe('UploadScheduleSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    importSeasonScheduleMock.mockReset();
    updateTournamentMock.mockReset();
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

  it('imports for the date range, then lets the operator save a per-row multiplier change', async () => {
    importSeasonScheduleMock.mockResolvedValue({
      created: [
        buildTournament({ id: 't-1', name: 'Sony Open', week: '1' }),
        buildTournament({ id: 't-2', name: 'The Masters', week: '15' }),
      ],
      skipped: [],
    });
    updateTournamentMock.mockImplementation((id, body) =>
      Promise.resolve(
        buildTournament({
          id,
          name: id === 't-2' ? 'The Masters' : 'Sony Open',
          week: id === 't-2' ? '15' : '1',
          payoutMultiplier: (body as { payoutMultiplier: number }).payoutMultiplier,
        }),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await user.type(screen.getByLabelText(/Start date/i), '2026-01-01');
    await user.type(screen.getByLabelText(/End date/i), '2026-12-31');
    await user.click(screen.getByRole('button', { name: /Import from ESPN/i }));

    await waitFor(() => {
      expect(importSeasonScheduleMock).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByText('Confirm Imported Tournaments')).toBeInTheDocument();
    const mastersRow = screen.getByText('The Masters').closest('tr')!;
    const multiplierInput = within(mastersRow).getByLabelText(
      /Payout multiplier for The Masters/i,
    ) as HTMLInputElement;
    const saveButton = within(mastersRow).getByRole('button', { name: /Save/i });
    expect(saveButton).toBeDisabled();

    await user.clear(multiplierInput);
    await user.type(multiplierInput, '2');
    expect(saveButton).toBeEnabled();
    await user.click(saveButton);

    await waitFor(() => {
      expect(updateTournamentMock).toHaveBeenCalledWith('t-2', { payoutMultiplier: 2 });
    });
    // Once saved, the row's draft equals the new server value, so Save returns to disabled.
    await waitFor(() => {
      expect(within(mastersRow).getByRole('button', { name: /Save/i })).toBeDisabled();
    });
  });

  it('disables the import button until a season + start date + end date are picked', async () => {
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Import from ESPN/i })).toBeDisabled();
  });
});
