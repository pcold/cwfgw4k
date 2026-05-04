import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LeagueSeasonPicker from './LeagueSeasonPicker';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeSeason } from '@/shared/test/fixtures';
import type { League, Season } from '@/shared/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

const leagues: League[] = [
  { id: 'lg-1', name: 'Castlewood', createdAt: '2026-01-01T00:00:00Z' },
  { id: 'lg-2', name: 'Shadow League', createdAt: '2026-01-01T00:00:00Z' },
];
const seasonsForLg1: Season[] = [
  makeSeason({ id: 'sn-1', leagueId: 'lg-1', name: 'Spring', seasonYear: 2026 }),
  makeSeason({
    id: 'sn-0',
    leagueId: 'lg-1',
    name: 'Fall',
    seasonYear: 2025,
    status: 'completed',
  }),
];
const seasonsForLg2: Season[] = [
  makeSeason({ id: 'sn-2', leagueId: 'lg-2', name: 'Shadow', seasonYear: 2026 }),
];

describe('LeagueSeasonPicker', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    leaguesMock.mockResolvedValue(leagues);
    seasonsMock.mockImplementation((id: string) =>
      Promise.resolve(id === 'lg-1' ? seasonsForLg1 : seasonsForLg2),
    );
  });

  it('renders nothing until leagues load', () => {
    renderWithProviders(<LeagueSeasonPicker />);
    expect(screen.queryByText(/League/i)).not.toBeInTheDocument();
  });

  it('renders league and season selects after the context resolves', async () => {
    renderWithProviders(<LeagueSeasonPicker />);
    expect(await screen.findByRole('combobox', { name: /League/i })).toBeInTheDocument();
    const seasonSelect = screen.getByRole('combobox', { name: /Season/i });
    await waitFor(() => {
      expect(seasonSelect).not.toBeDisabled();
    });
    expect(screen.getByRole('option', { name: '2026 Spring' })).toBeInTheDocument();
  });

  it('refetches seasons when the user picks a different league', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LeagueSeasonPicker />);

    const leagueSelect = await screen.findByRole('combobox', { name: /League/i });
    await user.selectOptions(leagueSelect, 'lg-2');

    await waitFor(() => {
      expect(seasonsMock).toHaveBeenCalledWith('lg-2');
    });
    expect(await screen.findByRole('option', { name: '2026 Shadow' })).toBeInTheDocument();
  });

  // The Live overlay checkbox lives per-page now (LiveOverlayCheckbox), only
  // visible when the active tournament selection is the season's earliest
  // non-finalized event. The picker no longer renders it.
});
