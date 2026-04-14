import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UploadScheduleSection from './UploadScheduleSection';
import { renderWithProviders } from '../../test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const uploadScheduleMock = vi.fn();

vi.mock('../../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    uploadSchedule: (input: unknown) => uploadScheduleMock(input),
  },
  ApiError: class ApiError extends Error {},
}));

describe('UploadScheduleSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    uploadScheduleMock.mockReset();
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

  it('uploads the schedule with the selected season year and renders the result table', async () => {
    uploadScheduleMock.mockResolvedValue({
      seasonYear: 2026,
      tournamentsCreated: 2,
      espnMatched: 1,
      espnUnmatched: ['The Invisible Open'],
      tournaments: [
        {
          id: 't-1',
          name: 'Sony Open',
          week: '1',
          startDate: '2026-01-15',
          endDate: '2026-01-18',
          payoutMultiplier: 1,
          espnId: '401',
          espnName: 'Sony Open',
        },
        {
          id: 't-2',
          name: 'The Masters',
          week: '15',
          startDate: '2026-04-09',
          endDate: '2026-04-12',
          payoutMultiplier: 2,
          espnId: null,
          espnName: null,
        },
      ],
    });

    const user = userEvent.setup();
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await user.type(
      screen.getByLabelText(/Schedule \(one tournament per line/i),
      '1 1 Jan 15-18 Sony Open',
    );
    await user.click(screen.getByRole('button', { name: /Upload & Validate with ESPN/i }));

    await waitFor(() => {
      expect(uploadScheduleMock).toHaveBeenCalledTimes(1);
    });
    expect(uploadScheduleMock).toHaveBeenCalledWith({
      seasonId: 'sn-1',
      seasonYear: 2026,
      schedule: '1 1 Jan 15-18 Sony Open',
    });

    expect(await screen.findByText('Season Created')).toBeInTheDocument();
    expect(screen.getByText(/The Invisible Open/)).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('Sony Open')).toBeInTheDocument();
    expect(within(table).getByText('The Masters')).toBeInTheDocument();
    expect(within(table).getByText('2x')).toBeInTheDocument();
    expect(within(table).getByText('No match')).toBeInTheDocument();
  });

  it('disables the upload button until a season is selected and text is entered', async () => {
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Upload & Validate with ESPN/i })).toBeDisabled();
  });
});
