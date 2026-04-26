import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UploadScheduleSection from './UploadScheduleSection';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const importSeasonScheduleMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    importSeasonSchedule: (input: unknown) => importSeasonScheduleMock(input),
  },
  ApiError: class ApiError extends Error {},
}));

describe('UploadScheduleSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    importSeasonScheduleMock.mockReset();
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

  it('imports the schedule from ESPN for the selected date range and renders the result table', async () => {
    importSeasonScheduleMock.mockResolvedValue({
      created: [
        {
          id: 't-1',
          pgaTournamentId: '401-sony',
          name: 'Sony Open',
          seasonId: 'sn-1',
          startDate: '2026-01-15',
          endDate: '2026-01-18',
          courseName: null,
          status: 'upcoming',
          purseAmount: null,
          payoutMultiplier: 1,
          week: '1',
          createdAt: '2026-01-01T00:00:00Z',
        },
        {
          id: 't-2',
          pgaTournamentId: null,
          name: 'The Masters',
          seasonId: 'sn-1',
          startDate: '2026-04-09',
          endDate: '2026-04-12',
          courseName: null,
          status: 'upcoming',
          purseAmount: null,
          payoutMultiplier: 2,
          week: '15',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      skipped: [
        { espnEventId: '401-skip', espnEventName: 'The Invisible Open', reason: 'already linked' },
      ],
    });

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
    expect(importSeasonScheduleMock).toHaveBeenCalledWith({
      seasonId: 'sn-1',
      startDate: '2026-01-01',
      endDate: '2026-12-31',
    });

    expect(await screen.findByText('Import Result')).toBeInTheDocument();
    const skippedItem = screen.getByRole('listitem');
    expect(within(skippedItem).getByText(/The Invisible Open/)).toBeInTheDocument();
    expect(within(skippedItem).getByText(/already linked/)).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('Sony Open')).toBeInTheDocument();
    expect(within(table).getByText('The Masters')).toBeInTheDocument();
    expect(within(table).getByText('2x')).toBeInTheDocument();
  });

  it('disables the import button until a season + start date + end date are picked', async () => {
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Import from ESPN/i })).toBeDisabled();
  });
});
