import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import type { api } from '@/shared/api/client';
import { nth } from '@/shared/test/nth';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import type { League, Season, SeasonRules, Tournament } from '@/shared/api/types';
import RulesPage from './RulesPage';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const tournamentsMock = vi.fn<typeof api.tournaments>();
const seasonRulesMock = vi.fn<typeof api.seasonRules>();

vi.mock('@/shared/api/client', () => ({
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
  tieFloor: 1,
  sideBetAmount: 15,
  maxTeams: 13,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
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

  it('stacks the rule cards in reading order, each under a header band', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    expect(screen.getByRole('heading', { name: /Season Rules/i })).toBeInTheDocument();
    await screen.findByRole('heading', { name: 'Weekly Payouts' });
    const bandTitles = screen.getAllByRole('heading', { level: 3 });
    expect(bandTitles.map((title) => title.textContent)).toEqual([
      'Weekly Payouts',
      'The Draft',
      'Zero-Sum Scoring',
      'Tie Handling',
      'Side Bets',
      'Season Schedule',
    ]);
    for (const title of bandTitles) {
      expect(title.parentElement).toHaveClass('bg-header-band', 'text-on-fill');
    }
  });

  it('lays the payouts out as one tile per place, first place in gold', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    const payoutList = await screen.findByRole('list', { name: 'Payouts by place' });
    expect(payoutList).toHaveClass('grid', 'grid-cols-5');
    const tiles = within(payoutList).getAllByRole('listitem');
    expect(tiles).toHaveLength(10);
    expect(within(nth(tiles, 0)).getByText('1st')).toBeInTheDocument();
    expect(within(nth(tiles, 0)).getByText('$18.00')).toBeInTheDocument();
    expect(within(nth(tiles, 1)).getByText('2nd')).toBeInTheDocument();
    expect(within(nth(tiles, 1)).getByText('$12.00')).toBeInTheDocument();
    expect(within(nth(tiles, 9)).getByText('10th')).toBeInTheDocument();
    expect(within(nth(tiles, 9)).getByText('$2.00')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('fills only the first-place tile with gold', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    const payoutList = await screen.findByRole('list', { name: 'Payouts by place' });
    const [firstPlace, ...field] = within(payoutList).getAllByRole('listitem');
    expect(firstPlace).toHaveClass('bg-gold');
    expect(within(nth(field, 0)).getByText('2nd')).toBeInTheDocument();
    for (const tile of field) {
      expect(tile).toHaveClass('bg-surface-raised');
      expect(tile).not.toHaveClass('bg-gold');
    }
  });

  it('renders season-specific payouts and side bets from the API', async () => {
    seasonRulesMock.mockResolvedValue({
      payouts: [25, 15, 10],
      tieFloor: 2,
      sideBetRounds: [6, 7],
      sideBetAmount: 20,
    });
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<RulesPage />);

    const payoutList = await screen.findByRole('list', { name: 'Payouts by place' });
    const tiles = within(payoutList).getAllByRole('listitem');
    expect(tiles).toHaveLength(3);
    expect(within(nth(tiles, 0)).getByText('$25.00')).toBeInTheDocument();
    expect(within(nth(tiles, 2)).getByText('3rd')).toBeInTheDocument();
    expect(screen.getByText('Top 3 drafted finishers')).toBeInTheDocument();
    expect(screen.getByText('Rounds 6, 7')).toBeInTheDocument();
    expect(screen.getByText('$20.00')).toBeInTheDocument();
    expect(screen.getByText('$2.00')).toBeInTheDocument();
  });

  it('captions the payouts band with the number of paid places', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    const caption = await screen.findByText('Top 10 drafted finishers');
    expect(caption.parentElement).toHaveClass('bg-header-band');
  });

  it('sets the zero-sum formula in a raised block', async () => {
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    const formula = await screen.findByText('Weekly +/− = (your earnings × teams) − total pot');
    expect(formula).toHaveClass('bg-surface-raised', 'rounded', 'font-semibold', 'text-ink');
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
    const section = scheduleHeading.closest('.bg-surface-card');
    if (!(section instanceof HTMLElement)) throw new Error('the Season Schedule heading has no card around it');
    const items = within(section).getAllByRole('listitem');
    expect(items).toHaveLength(3);

    expect(within(nth(items, 0)).getByText('Regular Open')).toBeInTheDocument();
    expect(within(nth(items, 0)).queryByText(/x$/)).not.toBeInTheDocument();

    expect(within(nth(items, 1)).getByText('The Masters')).toBeInTheDocument();
    expect(within(nth(items, 1)).getByText('2x')).toBeInTheDocument();

    expect(within(nth(items, 2)).getByText('US Open')).toBeInTheDocument();
    expect(within(nth(items, 2)).getByText('2x')).toBeInTheDocument();
  });

  it('sorts the season schedule by start date regardless of API order', async () => {
    tournamentsMock.mockResolvedValue([
      tournament({ id: 'tn-late', name: 'Late Event', startDate: '2026-07-01', week: '27' }),
      tournament({ id: 'tn-early', name: 'Early Event', startDate: '2026-02-01', week: '5' }),
    ]);

    renderWithProviders(<RulesPage />);

    await screen.findByText('Early Event');
    const scheduleSection = screen.getByRole('heading', { name: /Season Schedule/i }).closest('.bg-surface-card');
    if (!(scheduleSection instanceof HTMLElement)) {
      throw new Error('the Season Schedule heading has no card around it');
    }
    const items = within(scheduleSection).getAllByRole('listitem');
    expect(within(nth(items, 0)).getByText('Early Event')).toBeInTheDocument();
    expect(within(nth(items, 1)).getByText('Late Event')).toBeInTheDocument();
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

    const sideBetsTitle = await screen.findByRole('heading', { name: 'Side Bets' });
    expect(sideBetsTitle.parentElement).toHaveTextContent('Rounds 5, 6, 7, 8');
    expect(screen.getByText('$15.00')).toBeInTheDocument();
  });

  it('says no round carries a side bet for a season with no side-bet rounds, and lists no races', async () => {
    seasonRulesMock.mockResolvedValue({ ...defaultRules, sideBetRounds: [] });
    tournamentsMock.mockResolvedValue([]);
    renderWithProviders(<RulesPage />);

    expect(await screen.findByText('No draft round carries a side bet this season.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Side Bets' })).toBeInTheDocument();
    expect(screen.queryByText(/season-long races/i)).not.toBeInTheDocument();
  });

  it('shows the rules failure instead of payouts it cannot know, and keeps the cards that need no rules', async () => {
    seasonRulesMock.mockRejectedValue(new Error('network down'));
    tournamentsMock.mockResolvedValue([tournament({ name: 'The Masters' })]);

    renderWithProviders(<RulesPage />);

    expect(await screen.findAllByText(/Failed to load season rules/i)).toHaveLength(2);
    expect(screen.queryByRole('list', { name: 'Payouts by place' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'The Draft' })).toBeInTheDocument();
    expect(screen.getByText('Snake draft, each team drafts PGA Tour players')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Zero-Sum Scoring' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Tie Handling' })).not.toBeInTheDocument();
    expect(await screen.findByText('The Masters')).toBeInTheDocument();
  });

  it('shows a schedule failure rather than an empty schedule', async () => {
    tournamentsMock.mockRejectedValue(new Error('network down'));

    renderWithProviders(<RulesPage />);

    expect(await screen.findByText(/Failed to load schedule/i)).toBeInTheDocument();
    expect(screen.queryByText(/No tournaments scheduled/i)).not.toBeInTheDocument();
    expect(await screen.findByRole('list', { name: 'Payouts by place' })).toBeInTheDocument();
  });

  it('shows no payouts for a league without a season', async () => {
    seasonsMock.mockResolvedValue([]);
    tournamentsMock.mockResolvedValue([]);

    renderWithProviders(<RulesPage />);

    expect(await screen.findByRole('heading', { name: /The Draft/i })).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Payouts by place' })).not.toBeInTheDocument();
    expect(seasonRulesMock).not.toHaveBeenCalled();
  });

  it('surfaces a leagues error without blowing up', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<RulesPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });
});
