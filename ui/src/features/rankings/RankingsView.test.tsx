import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import type { Rankings } from '@/shared/api/types';
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
    ...overrides,
  };
}

describe('RankingsView', () => {
  it('numbers the teams in the order the server sent them, without re-sorting by totalCash', () => {
    render(<RankingsView rankings={buildRankings()} />);
    const table = screen.getByRole('table');
    const rows = within(table).getAllByRole('row').slice(1); // drop header row
    const ranked = rows.map((row) => {
      const [rank, name] = within(row).getAllByRole('cell');
      return `${rank?.textContent ?? ''} ${name?.textContent ?? ''}`;
    });
    expect(ranked).toEqual(['1 Aces', '2 Birdies', '3 Cuts']);
  });

  it('renders the cumulative totals chart with a legend entry per team', () => {
    render(<RankingsView rankings={buildRankings()} />);
    const chart = screen.getByRole('figure', { name: /Cumulative Totals by Week/i });
    expect(within(chart).getByText('Aces')).toBeInTheDocument();
    expect(within(chart).getByText('Birdies')).toBeInTheDocument();
    expect(within(chart).getByText('Cuts')).toBeInTheDocument();
  });

  it('shows an empty chart message when there is no week history yet', () => {
    render(
      <RankingsView
        rankings={buildRankings({ teams: [], weeks: [], tournamentNames: [] })}
      />,
    );
    expect(screen.getByText(/No ranking history yet/i)).toBeInTheDocument();
  });

  it('shows Total Cash in whole dollars and the subtotal and late row bets in cents', () => {
    const [aces, ...rest] = buildRankings().teams;
    if (!aces) throw new Error('fixture has no first team');
    const fractional = { ...aces, subtotal: 751.5715, sideBets: -1.7785, totalCash: 1082.4 };
    render(<RankingsView rankings={buildRankings({ teams: [fractional, ...rest] })} />);
    const row = screen.getByRole('row', { name: /Aces/ });
    expect(within(row).getByText('+$751.57')).toBeInTheDocument();
    expect(within(row).getByText('\u2212$1.78')).toBeInTheDocument();
    expect(within(row).getByText('+$1,082')).toBeInTheDocument();
  });

  it('shows the won, lost and net the server totalled, not a sum of the team rows', () => {
    const cashTotals = { won: 1234.4, lost: -987.6, net: 246.8 };
    render(<RankingsView rankings={buildRankings({ cashTotals })} />);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent(/^\+\$1,234$/);
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent(/^\u2212\$988$/);
    expect(screen.getByText('Net').nextSibling).toHaveTextContent(/^\+\$247$/);
  });

  it('colours only Total Cash by sign, and leaves Subtotal and Late Row Bets in the table ink', () => {
    render(<RankingsView rankings={buildRankings()} />);
    const aces = screen.getByRole('row', { name: /Aces/ });
    const birdies = screen.getByRole('row', { name: /Birdies/ });
    expect(within(aces).getByText('+$150')).toHaveClass('text-semantic-success');
    expect(within(birdies).getByText('\u2212$35')).toHaveClass('text-semantic-danger');
    expect(within(aces).getByText('+$120.00').className).not.toMatch(/text-semantic-|text-ink-soft/);
    expect(within(birdies).getByText('\u2212$15.00').className).not.toMatch(/text-semantic-|text-ink-soft/);
  });

  it('mutes a Total Cash that rounds to $0, and tones the summary chips by the sign they print', () => {
    const [aces, ...rest] = buildRankings().teams;
    if (!aces) throw new Error('fixture has no first team');
    const cashTotals = { won: 35, lost: -35.4, net: -0.4 };
    render(<RankingsView rankings={buildRankings({ teams: [{ ...aces, totalCash: -0.4 }, ...rest], cashTotals })} />);
    expect(within(screen.getByRole('row', { name: /Aces/ })).getByText('$0')).toHaveClass('text-ink-soft');
    expect(screen.getByText('Won').nextSibling).toHaveClass('text-semantic-success');
    expect(screen.getByText('Lost').nextSibling).toHaveClass('text-semantic-danger');
    expect(screen.getByText('Net').nextSibling).toHaveClass('text-ink-soft');
  });
});
