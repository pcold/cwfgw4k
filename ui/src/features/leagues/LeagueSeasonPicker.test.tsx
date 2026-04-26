import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LeagueSeasonPicker from './LeagueSeasonPicker';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
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
  {
    id: 'sn-1',
    leagueId: 'lg-1',
    name: 'Spring',
    seasonYear: 2026,
    seasonNumber: 1,
    status: 'active',
  },
  {
    id: 'sn-0',
    leagueId: 'lg-1',
    name: 'Fall',
    seasonYear: 2025,
    seasonNumber: 1,
    status: 'completed',
  },
];
const seasonsForLg2: Season[] = [
  {
    id: 'sn-2',
    leagueId: 'lg-2',
    name: 'Shadow',
    seasonYear: 2026,
    seasonNumber: 1,
    status: 'active',
  },
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

  it('toggles the live overlay checkbox', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LeagueSeasonPicker />);
    const checkbox = await screen.findByRole('checkbox', { name: /Live overlay/i });
    expect(checkbox).toBeChecked();
    await user.click(checkbox);
    expect(checkbox).not.toBeChecked();
  });
});
