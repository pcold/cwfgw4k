import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import type { Rankings } from '@/shared/api/types';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import RankingsView from './RankingsView';

function buildRankings(overrides: Partial<Rankings> = {}): Rankings {
  return {
    teams: [
      {
        teamId: 'team-1',
        teamName: 'Aces',
        subtotal: 120,
        sideBets: 30,
        totalCash: 150,
        series: [50, 100, 150],
      },
      {
        teamId: 'team-2',
        teamName: 'Birdies',
        subtotal: -20,
        sideBets: -15,
        totalCash: -35,
        series: [0, -20, -35],
      },
      {
        teamId: 'team-3',
        teamName: 'Cuts',
        subtotal: 40,
        sideBets: -5,
        totalCash: 35,
        series: [10, 20, 35],
      },
    ],
    weeks: ['Week 1', 'Week 2', 'Week 3'],
    tournamentNames: ['A', 'B', 'C'],
    live: false,
    liveEligible: false,
    cashTotals: { won: 185, lost: -35, net: 150 },
    standings: [
      { type: 'leader', teamId: 'team-1', tied: false, aheadOf: { position: 2, tied: false, amount: 115 } },
      {
        type: 'chaser',
        teamId: 'team-2',
        tied: false,
        position: 3,
        behind: { position: 2, tied: false, amount: 70 },
        behindLeader: 185,
      },
      {
        type: 'chaser',
        teamId: 'team-3',
        tied: false,
        position: 2,
        behind: { position: 1, tied: false, amount: 115 },
        behindLeader: 115,
      },
    ],
    ...overrides,
  };
}

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

function phoneCards(): HTMLElement[] {
  return within(screen.getByRole('list', { name: 'Standings' })).getAllByRole('listitem');
}

function phoneCard(teamName: string): HTMLElement {
  const card = within(screen.getByRole('list', { name: 'Standings' })).getByText(teamName).closest('li');
  if (!card) throw new Error(`no phone card for ${teamName}`);
  return card;
}

describe('RankingsView', () => {
  it('numbers the teams in the order the server sent them, without re-sorting by totalCash', () => {
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);
    const table = screen.getByRole('table');
    const rows = within(table).getAllByRole('row').slice(1); // drop header row
    const ranked = rows.map((row) => {
      const [rank, name] = within(row).getAllByRole('cell');
      return `${rank?.textContent ?? ''} ${name?.textContent ?? ''}`;
    });
    expect(ranked).toEqual(['1 Aces', '2 Birdies', '3 Cuts']);
  });

  it('renders the cumulative totals chart with a legend entry per team', () => {
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);
    const chart = screen.getByRole('figure', { name: /Cumulative Totals by Week/i });
    expect(within(chart).getByText('Aces')).toBeInTheDocument();
    expect(within(chart).getByText('Birdies')).toBeInTheDocument();
    expect(within(chart).getByText('Cuts')).toBeInTheDocument();
  });

  it('shows an empty chart message when there is no week history yet', () => {
    renderWithProviders(
      <RankingsView myTeamName={null} rankings={buildRankings({ teams: [], weeks: [], tournamentNames: [] })} />,
      NO_PROVIDERS,
    );
    expect(screen.getByText(/No ranking history yet/i)).toBeInTheDocument();
  });

  it('shows Total Cash in whole dollars and the subtotal and late row bets in cents', () => {
    const [aces, ...rest] = buildRankings().teams;
    if (!aces) throw new Error('fixture has no first team');
    const fractional = { ...aces, subtotal: 751.5715, sideBets: -1.7785, totalCash: 1082.4 };
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings({ teams: [fractional, ...rest] })} />, NO_PROVIDERS);
    const row = screen.getByRole('row', { name: /Aces/ });
    expect(within(row).getByText('+$751.57')).toBeInTheDocument();
    expect(within(row).getByText('\u2212$1.78')).toBeInTheDocument();
    expect(within(row).getByText('+$1,082')).toBeInTheDocument();
  });

  it('shows the won, lost and net the server totalled, not a sum of the team rows', () => {
    const cashTotals = { won: 1234.4, lost: -987.6, net: 246.8 };
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings({ cashTotals })} />, NO_PROVIDERS);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent(/^\+\$1,234$/);
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent(/^\u2212\$988$/);
    expect(screen.getByText('Net').nextSibling).toHaveTextContent(/^\+\$247$/);
  });

  it('fills each Total Cash box with the tone of its sign, and leaves Subtotal and Late Row Bets in the table ink', () => {
    const [aces, birdies, cuts] = buildRankings().teams;
    if (!aces || !birdies || !cuts) throw new Error('fixture has fewer than three teams');
    renderWithProviders(
      <RankingsView myTeamName={null} rankings={buildRankings({ teams: [aces, birdies, { ...cuts, totalCash: 0.4 }] })} />,
      NO_PROVIDERS,
    );
    expect(within(screen.getByRole('row', { name: /Aces/ })).getByText('+$150')).toHaveClass('bg-money-up-fill');
    expect(within(screen.getByRole('row', { name: /Birdies/ })).getByText('\u2212$35')).toHaveClass('bg-money-down-fill');
    expect(within(screen.getByRole('row', { name: /Cuts/ })).getByText('$0')).toHaveClass('bg-money-even-fill');
    const aceCells = within(screen.getByRole('row', { name: /Aces/ }));
    expect(aceCells.getByText('+$120.00').className).not.toMatch(/text-semantic-|text-ink-soft|bg-money-/);
    expect(aceCells.getByText('+$30.00').className).not.toMatch(/text-semantic-|text-ink-soft|bg-money-/);
    const birdieCells = within(screen.getByRole('row', { name: /Birdies/ }));
    expect(birdieCells.getByText('\u2212$15.00').className).not.toMatch(/text-semantic-|text-ink-soft|bg-money-/);
  });

  it('sets each team as a white card between its position and money boxes, with no row hover', () => {
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);
    expect(screen.getByRole('table')).toHaveClass('border-separate', 'border-spacing-y-1');
    const row = screen.getByRole('row', { name: /Aces/ });
    expect(row).toHaveClass('h-11');
    expect(row.className).not.toMatch(/hover:/);
    const [, name, subtotal, sideBets] = within(row).getAllByRole('cell');
    for (const cardCell of [name, subtotal, sideBets]) expect(cardCell).toHaveClass('bg-surface-card');
  });

  it('boxes the first position in gold and every other in navy', () => {
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);
    const positions = within(screen.getByRole('table'))
      .getAllByRole('row')
      .slice(1)
      .map((row) => within(row).getAllByRole('cell')[0]);
    expect(positions[0]).toHaveClass('bg-gold');
    expect(positions[1]).toHaveClass('bg-position-fill');
    expect(positions[2]).toHaveClass('bg-position-fill');
  });

  it('tones the summary chips by the sign they print, muting one that rounds to $0', () => {
    const cashTotals = { won: 35, lost: -35.4, net: -0.4 };
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings({ cashTotals })} />, NO_PROVIDERS);
    expect(screen.getByText('Won').nextSibling).toHaveClass('text-semantic-success');
    expect(screen.getByText('Lost').nextSibling).toHaveClass('text-semantic-danger');
    expect(screen.getByText('Net').nextSibling).toHaveClass('text-ink-soft');
  });

  it('sets every column head in the shared column-header type', () => {
    renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);
    expectSharedColumnHeads(1);
  });

  describe("the visitor's own team", () => {
    it('tags its row and its card and no other, matching the name whatever its case', () => {
      renderWithProviders(<RankingsView myTeamName="CUTS" rankings={buildRankings()} />, NO_PROVIDERS);

      expect(within(screen.getByRole('row', { name: /Cuts/ })).getByText('your team')).toBeInTheDocument();
      expect(within(phoneCard('Cuts')).getByText('your team')).toBeInTheDocument();
      expect(screen.getAllByText('your team')).toHaveLength(2);
    });

    // Styling alone, so the classes are what there is to assert.
    it('rings its row in gold and fills the cells between the boxes pale gold', () => {
      renderWithProviders(<RankingsView myTeamName="CUTS" rankings={buildRankings()} />, NO_PROVIDERS);

      const row = screen.getByRole('row', { name: /Cuts/ });
      expect(row).toHaveClass('outline-2', '-outline-offset-2', 'outline-gold');
      const [, name, subtotal, sideBets] = within(row).getAllByRole('cell');
      for (const cardCell of [name, subtotal, sideBets]) expect(cardCell).toHaveClass('bg-my-team');
      expect(screen.getByRole('row', { name: /Aces/ })).not.toHaveClass('outline-2');
      expect(phoneCard('Cuts')).toHaveClass('bg-my-team', 'ring-gold');
    });

    it('marks nothing when no team is chosen', () => {
      renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);

      expect(screen.queryByText('your team')).toBeNull();
      expect(screen.getByRole('row', { name: /Cuts/ })).not.toHaveClass('outline-2');
    });
  });

  describe('the your-team card', () => {
    function yourTeamCard(): HTMLElement {
      return screen.getByRole('region', { name: 'Your team' });
    }

    function statsOf(card: HTMLElement): string[] {
      return within(card)
        .getAllByRole('term')
        .map((term) => `${term.textContent}: ${term.nextElementSibling?.textContent ?? ''}`);
    }

    it('opens the standings with a chasing team, its total box and three stats from its standing', () => {
      renderWithProviders(<RankingsView myTeamName="birdies" rankings={buildRankings()} />, NO_PROVIDERS);

      const card = yourTeamCard();
      expect(within(card).getByText('Birdies')).toBeInTheDocument();
      expect(within(card).getByText('\u2212$35')).toHaveClass('bg-money-down-fill');
      expect(statsOf(card)).toEqual(['Pos: 3rd / 3', 'Behind 2nd: +$70', 'Behind leader: +$185']);
    });

    it('gives the leader two stats: its position and its lead', () => {
      renderWithProviders(<RankingsView myTeamName="Aces" rankings={buildRankings()} />, NO_PROVIDERS);

      expect(statsOf(yourTeamCard())).toEqual(['Pos: 1st / 3', 'Ahead of 2nd: +$115']);
    });

    it('shows no card when no team is chosen', () => {
      renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);

      expect(screen.queryByRole('region', { name: 'Your team' })).toBeNull();
    });

    it('shows no card for a team these standings do not hold', () => {
      renderWithProviders(<RankingsView myTeamName="Eagles" rankings={buildRankings()} />, NO_PROVIDERS);

      expect(screen.queryByRole('region', { name: 'Your team' })).toBeNull();
      expect(screen.getByRole('table')).toBeInTheDocument();
    });
  });

  describe('on a phone', () => {
    it('shows the cards below sm and the table from sm up', () => {
      renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);

      expect(screen.getByRole('list', { name: 'Standings' })).toHaveClass('sm:hidden');
      expect(screen.getByRole('table').parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('sets a card per team in the order the server sent them', () => {
      renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);

      const cards = phoneCards().map((card) => {
        const [position, middle] = Array.from(card.children);
        return `${position?.textContent ?? ''} ${middle?.firstElementChild?.textContent ?? ''}`;
      });
      expect(cards).toEqual(['1 Aces', '2 Birdies', '3 Cuts']);
    });

    it('boxes the first position in gold and every other in navy', () => {
      renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings()} />, NO_PROVIDERS);

      const positions = phoneCards().map((card) => card.firstElementChild);
      expect(positions[0]).toHaveClass('bg-gold');
      expect(positions[1]).toHaveClass('bg-position-fill');
      expect(positions[2]).toHaveClass('bg-position-fill');
      expect(positions[1]).not.toHaveClass('bg-gold');
    });

    it('prints the subtotal and late row bets under the name, in cents as the table does', () => {
      const [aces, ...rest] = buildRankings().teams;
      if (!aces) throw new Error('fixture has no first team');
      const fractional = { ...aces, subtotal: 751.5715, sideBets: -1.7785 };
      renderWithProviders(<RankingsView myTeamName={null} rankings={buildRankings({ teams: [fractional, ...rest] })} />, NO_PROVIDERS);

      expect(within(phoneCard('Aces')).getByText(/^Sub /)).toHaveTextContent(/^Sub \+\$751\.57 · Bets \u2212\$1\.78$/);
      expect(within(phoneCard('Birdies')).getByText(/^Sub /)).toHaveTextContent(/^Sub \u2212\$20\.00 · Bets \u2212\$15\.00$/);
    });

    it('fills each Total Cash box with the tone of its sign, in whole dollars', () => {
      const [aces, birdies, cuts] = buildRankings().teams;
      if (!aces || !birdies || !cuts) throw new Error('fixture has fewer than three teams');
      renderWithProviders(
        <RankingsView myTeamName={null} rankings={buildRankings({ teams: [aces, birdies, { ...cuts, totalCash: 0.4 }] })} />,
        NO_PROVIDERS,
      );

      expect(within(phoneCard('Aces')).getByText('+$150')).toHaveClass('bg-money-up-fill', 'rounded-r');
      expect(within(phoneCard('Birdies')).getByText('\u2212$35')).toHaveClass('bg-money-down-fill');
      expect(within(phoneCard('Cuts')).getByText('$0')).toHaveClass('bg-money-even-fill');
    });
  });
});
