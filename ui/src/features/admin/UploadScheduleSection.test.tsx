import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UploadScheduleSection from './UploadScheduleSection';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const previewSeasonScheduleMock = vi.fn();
const confirmSeasonScheduleMock = vi.fn();
const updateTournamentMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    previewSeasonSchedule: (input: unknown) => previewSeasonScheduleMock(input),
    confirmSeasonSchedule: (input: unknown) => confirmSeasonScheduleMock(input),
    updateTournament: (id: string, body: unknown) => updateTournamentMock(id, body),
  },
  ApiError: class ApiError extends Error {},
}));

function buildPreviewEntry(overrides: { espnEventId: string; name: string; week: string }) {
  return {
    espnEventId: overrides.espnEventId,
    name: overrides.name,
    startDate: '2026-04-09',
    endDate: '2026-04-12',
    week: overrides.week,
  };
}

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
    previewSeasonScheduleMock.mockReset();
    confirmSeasonScheduleMock.mockReset();
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

  async function previewSchedule(user: ReturnType<typeof userEvent.setup>) {
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await user.type(screen.getByLabelText(/Start date/i), '2026-01-01');
    await user.type(screen.getByLabelText(/End date/i), '2026-12-31');
    await user.click(screen.getByRole('button', { name: /Preview Import/i }));
    await waitFor(() => {
      expect(previewSeasonScheduleMock).toHaveBeenCalledTimes(1);
    });
  }

  it('previews candidates, drops an unwanted one, then confirms only the kept entries', async () => {
    previewSeasonScheduleMock.mockResolvedValue({
      entries: [
        buildPreviewEntry({ espnEventId: 'e-1', name: 'Sony Open', week: '1' }),
        buildPreviewEntry({ espnEventId: 'e-2', name: 'Presidents Cup', week: '2' }),
      ],
      skipped: [],
    });
    confirmSeasonScheduleMock.mockResolvedValue({
      created: [buildTournament({ id: 't-1', name: 'Sony Open', week: '1' })],
      skipped: [],
    });

    const user = userEvent.setup();
    await previewSchedule(user);

    expect(await screen.findByText('Review Before Confirming')).toBeInTheDocument();
    const cupRow = screen.getByText('Presidents Cup').closest('tr');
    if (!cupRow) throw new Error('row not found');
    await user.click(within(cupRow).getByRole('button', { name: /Remove/i }));
    expect(screen.queryByText('Presidents Cup')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Confirm Import/i }));

    await waitFor(() => {
      expect(confirmSeasonScheduleMock).toHaveBeenCalledWith({
        seasonId: 'sn-1',
        entries: [
          {
            espnEventId: 'e-1',
            name: 'Sony Open',
            startDate: '2026-04-09',
            endDate: '2026-04-12',
          },
        ],
      });
    });
    expect(await screen.findByText('Confirm Imported Tournaments')).toBeInTheDocument();
    expect(screen.queryByText('Review Before Confirming')).not.toBeInTheDocument();
  });

  it('lets the operator save a per-row multiplier change after confirming', async () => {
    previewSeasonScheduleMock.mockResolvedValue({
      entries: [buildPreviewEntry({ espnEventId: 'e-1', name: 'The Masters', week: '15' })],
      skipped: [],
    });
    confirmSeasonScheduleMock.mockResolvedValue({
      created: [buildTournament({ id: 't-2', name: 'The Masters', week: '15' })],
      skipped: [],
    });
    updateTournamentMock.mockImplementation((id, body) =>
      Promise.resolve(
        buildTournament({
          id,
          name: 'The Masters',
          week: '15',
          payoutMultiplier: (body as { payoutMultiplier: number }).payoutMultiplier,
        }),
      ),
    );

    const user = userEvent.setup();
    await previewSchedule(user);
    await user.click(await screen.findByRole('button', { name: /Confirm Import/i }));
    await waitFor(() => {
      expect(confirmSeasonScheduleMock).toHaveBeenCalledTimes(1);
    });

    const mastersRow = (await screen.findByText('The Masters')).closest('tr');
    if (!mastersRow) throw new Error('row not found');
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

  it('disables the preview button until a season + start date + end date are picked', async () => {
    renderWithProviders(<UploadScheduleSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Preview Import/i })).toBeDisabled();
  });

  it('disables confirm and shows a message when every candidate has been removed', async () => {
    previewSeasonScheduleMock.mockResolvedValue({
      entries: [buildPreviewEntry({ espnEventId: 'e-1', name: 'Presidents Cup', week: '1' })],
      skipped: [],
    });

    const user = userEvent.setup();
    await previewSchedule(user);

    const cupRow = (await screen.findByText('Presidents Cup')).closest('tr');
    if (!cupRow) throw new Error('row not found');
    await user.click(within(cupRow).getByRole('button', { name: /Remove/i }));

    expect(await screen.findByText(/Every candidate was removed/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Confirm Import/i })).toBeDisabled();
  });
});
