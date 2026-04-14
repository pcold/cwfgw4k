import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import RulesPage from './RulesPage';
import type { League, Season, Tournament } from '../api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();

vi.mock('../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    tournaments: (id: string) => tournamentsMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

const league: League = { id: 'lg-1', name: 'Test League', createdAt: '2026-01-01T00:00:00Z' };
const season: Season = {
  id: 'sn-1',
  leagueId: 'lg-1',
  name: 'Season 1',
  seasonYear: 2026,
  seasonNumber: 1,
  status: 'active',
};

function tournament(overrides: Partial<Tournament> = {}): Tournament {
  return {
    id: 'tn-1',
    pgaTournamentId: null,
    name: 'Regular Open',
    seasonId: 'sn-1',
    startDate: '2026-03-01',
    endDate: '2026-03-04',
    courseName: null,
    status: 'completed',
    purseAmount: null,
    payoutMultiplier: 1,
    week: '9',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('RulesPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
  });

  it('renders the static rule sections with default payout values', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    expect(
      await screen.findByRole('heading', { name: /Season Rules/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /The Draft/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Weekly Payouts/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Zero-Sum Scoring/i })).toBeInTheDocument();

    // First place payout = $18, tie floor = $1, side bet = $15 (from DEFAULT_RULES)
    const payoutTable = screen.getByRole('table');
    const payoutRows = within(payoutTable).getAllByRole('row').slice(1);
    expect(payoutRows).toHaveLength(10);
    expect(within(payoutRows[0]).getByText('1st')).toBeInTheDocument();
    expect(within(payoutRows[0]).getByText('$18')).toBeInTheDocument();
    expect(within(payoutRows[9]).getByText('10th')).toBeInTheDocument();
  });

  it('lists multiplier tournaments when any exist', async () => {
    tournamentsMock.mockResolvedValue([
      tournament({ id: 'tn-1', name: 'Regular Open', payoutMultiplier: 1 }),
      tournament({ id: 'tn-2', name: 'The Masters', payoutMultiplier: 2 }),
      tournament({ id: 'tn-3', name: 'US Open', payoutMultiplier: 2 }),
    ]);

    renderWithProviders(<RulesPage />);

    await screen.findByText(/The Masters/);
    const multiplierHeading = screen.getByRole('heading', { name: /Multiplier Tournaments/i });
    const section = multiplierHeading.parentElement!;
    expect(within(section).getByText(/US Open/)).toBeInTheDocument();
    expect(within(section).queryByText(/Regular Open/)).not.toBeInTheDocument();
    expect(within(section).getAllByRole('listitem')).toHaveLength(2);
  });

  it('shows the empty state when no tournament has a non-default multiplier', async () => {
    tournamentsMock.mockResolvedValue([tournament({ payoutMultiplier: 1 })]);

    renderWithProviders(<RulesPage />);

    expect(
      await screen.findByText(/No multiplier tournaments in this season/i),
    ).toBeInTheDocument();
  });

  it('renders the side bets section with the configured rounds and amount', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    expect(
      await screen.findByRole('heading', { name: /Side Bets \(Rounds 5, 6, 7, 8\)/i }),
    ).toBeInTheDocument();
    expect(screen.getByText('$15')).toBeInTheDocument();
  });

  it('surfaces a leagues error without blowing up', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<RulesPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });
});
