import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import RankingsView from './RankingsView';
import type { Rankings } from '../api/types';

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
        liveWeekly: null,
      },
      {
        teamId: 'team-2',
        teamName: 'Birdies',
        subtotal: -20,
        sideBets: -15,
        totalCash: -35,
        series: [0, -20, -35],
        liveWeekly: null,
      },
      {
        teamId: 'team-3',
        teamName: 'Cuts',
        subtotal: 40,
        sideBets: -5,
        totalCash: 35,
        series: [10, 20, 35],
        liveWeekly: null,
      },
    ],
    weeks: ['Week 1', 'Week 2', 'Week 3'],
    tournamentNames: ['A', 'B', 'C'],
    live: false,
    ...overrides,
  };
}

describe('RankingsView', () => {
  it('sorts teams by totalCash descending', () => {
    render(<RankingsView rankings={buildRankings()} />);
    const rows = screen.getAllByRole('row').slice(1); // drop header row
    const names = rows.map((row) => within(row).getAllByRole('cell')[1]?.textContent);
    expect(names).toEqual(['Aces', 'Cuts', 'Birdies']);
  });

  it('computes the zero-sum won/lost/net summary', () => {
    render(<RankingsView rankings={buildRankings()} />);
    // Won = 150 + 35 = 185, Lost = -35, Net = 150
    expect(screen.getByText('Won').nextSibling).toHaveTextContent('$185');
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent('-$35');
    expect(screen.getByText('Net').nextSibling).toHaveTextContent('$150');
  });

  it('surfaces the live overlay indicator when enabled', () => {
    render(<RankingsView rankings={buildRankings({ live: true })} />);
    expect(screen.getByText(/live overlay on/i)).toBeInTheDocument();
  });

  it('omits the live indicator by default', () => {
    render(<RankingsView rankings={buildRankings()} />);
    expect(screen.queryByText(/live overlay on/i)).not.toBeInTheDocument();
  });

  it('shows the number of weeks played', () => {
    render(<RankingsView rankings={buildRankings()} />);
    expect(screen.getByText(/3 weeks played/i)).toBeInTheDocument();
  });
});
