import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import RulesPage from './RulesPage';
import type { League, Season, SeasonRules, Tournament } from '@/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const seasonRulesMock = vi.fn();

vi.mock('../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    tournaments: (id: string) => tournamentsMock(id),
    seasonRules: (id: string) => seasonRulesMock(id),
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

const defaultRules: SeasonRules = {
  payouts: [18, 12, 10, 8, 7, 6, 5, 4, 3, 2],
  tieFloor: 1,
  sideBetRounds: [5, 6, 7, 8],
  sideBetAmount: 15,
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
    seasonRulesMock.mockReset();
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    seasonRulesMock.mockResolvedValue(defaultRules);
  });

  it('renders the rule sections using the season rules from the API', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    expect(
      await screen.findByRole('heading', { name: /Season Rules/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /The Draft/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Weekly Payouts/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Zero-Sum Scoring/i })).toBeInTheDocument();

    // First place payout from the API rules
    const payoutTable = screen.getByRole('table');
    const payoutRows = within(payoutTable).getAllByRole('row').slice(1);
    expect(payoutRows).toHaveLength(10);
    expect(within(payoutRows[0]).getByText('1st')).toBeInTheDocument();
    expect(within(payoutRows[0]).getByText('$18')).toBeInTheDocument();
    expect(within(payoutRows[9]).getByText('10th')).toBeInTheDocument();
  });

  it('uses season-specific payouts from the API instead of defaults', async () => {
    seasonRulesMock.mockResolvedValue({
      payouts: [25, 15, 10],
      tieFloor: 2,
      sideBetRounds: [6, 7],
      sideBetAmount: 20,
    });
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<RulesPage />);

    // Wait for the season-specific payouts to replace the default fallback
    await screen.findByText('$25');
    const payoutTable = screen.getByRole('table');
    const payoutRows = within(payoutTable).getAllByRole('row').slice(1);
    expect(payoutRows).toHaveLength(3);
    expect(
      screen.getByRole('heading', { name: /Side Bets \(Rounds 6, 7\)/i }),
    ).toBeInTheDocument();
    expect(screen.getByText('$20')).toBeInTheDocument();
  });

  it('lists every tournament in the season schedule with multiplier badges', async () => {
    tournamentsMock.mockResolvedValue([
      tournament({
        id: 'tn-1',
        name: 'Regular Open',
        payoutMultiplier: 1,
        week: '9',
        startDate: '2026-03-01',
      }),
      tournament({
        id: 'tn-2',
        name: 'The Masters',
        payoutMultiplier: 2,
        week: '10',
        startDate: '2026-04-09',
      }),
      tournament({
        id: 'tn-3',
        name: 'US Open',
        payoutMultiplier: 2,
        week: '24',
        startDate: '2026-06-18',
      }),
    ]);

    renderWithProviders(<RulesPage />);

    await screen.findByText(/The Masters/);
    const scheduleHeading = screen.getByRole('heading', { name: /Season Schedule/i });
    const section = scheduleHeading.parentElement!;
    const items = within(section).getAllByRole('listitem');
    expect(items).toHaveLength(3);

    expect(within(items[0]).getByText('Regular Open')).toBeInTheDocument();
    expect(within(items[0]).queryByText(/x$/)).not.toBeInTheDocument();

    expect(within(items[1]).getByText('The Masters')).toBeInTheDocument();
    expect(within(items[1]).getByText('2x')).toBeInTheDocument();

    expect(within(items[2]).getByText('US Open')).toBeInTheDocument();
    expect(within(items[2]).getByText('2x')).toBeInTheDocument();
  });

  it('sorts the season schedule by start date regardless of API order', async () => {
    tournamentsMock.mockResolvedValue([
      tournament({ id: 'tn-late', name: 'Late Event', startDate: '2026-07-01', week: '27' }),
      tournament({ id: 'tn-early', name: 'Early Event', startDate: '2026-02-01', week: '5' }),
    ]);

    renderWithProviders(<RulesPage />);

    await screen.findByText('Early Event');
    const scheduleSection = screen.getByRole('heading', {
      name: /Season Schedule/i,
    }).parentElement!;
    const items = within(scheduleSection).getAllByRole('listitem');
    expect(within(items[0]).getByText('Early Event')).toBeInTheDocument();
    expect(within(items[1]).getByText('Late Event')).toBeInTheDocument();
  });

  it('shows the empty state when the season has no tournaments', async () => {
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<RulesPage />);

    expect(
      await screen.findByText(/No tournaments scheduled for this season/i),
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

  it('falls back to default rules when the rules query fails', async () => {
    seasonRulesMock.mockRejectedValue(new Error('network down'));
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<RulesPage />);

    // Still renders with defaults (first payout = $18)
    const payoutTable = await screen.findByRole('table');
    const payoutRows = within(payoutTable).getAllByRole('row').slice(1);
    expect(within(payoutRows[0]).getByText('$18')).toBeInTheDocument();
  });

  it('surfaces a leagues error without blowing up', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<RulesPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });
});
