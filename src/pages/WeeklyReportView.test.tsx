import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import WeeklyReportView from './WeeklyReportView';
import type { WeeklyReport } from '../api/types';

function buildReport(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
  return {
    tournament: {
      id: 't-1',
      name: 'The Players Championship',
      startDate: '2026-03-12',
      endDate: '2026-03-15',
      status: 'completed',
      payoutMultiplier: 2,
      week: '9',
    },
    teams: [
      {
        teamId: 'team-1',
        teamName: 'Aces',
        ownerName: 'Alice',
        rows: [],
        topTens: 0,
        weeklyTotal: 25,
        previous: 100,
        subtotal: 125,
        topTenCount: 1,
        topTenMoney: 18,
        sideBets: 45,
        totalCash: 170,
      },
      {
        teamId: 'team-2',
        teamName: 'Birdies',
        ownerName: 'Bob',
        rows: [],
        topTens: 0,
        weeklyTotal: -25,
        previous: 90,
        subtotal: 65,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: -15,
        totalCash: 50,
      },
    ],
    undraftedTopTens: [],
    sideBetDetail: [],
    standingsOrder: [
      { rank: 1, teamName: 'Aces', totalCash: 170 },
      { rank: 2, teamName: 'Birdies', totalCash: 50 },
    ],
    live: false,
    ...overrides,
  };
}

describe('WeeklyReportView', () => {
  it('renders the tournament header with payout multiplier', () => {
    render(<WeeklyReportView report={buildReport()} />);
    expect(screen.getByRole('heading', { name: /Players Championship/i })).toBeInTheDocument();
    expect(screen.getByText(/2x payouts/)).toBeInTheDocument();
  });

  it('shows a row per team with formatted totals', () => {
    render(<WeeklyReportView report={buildReport()} />);
    const table = screen.getByRole('table');
    expect(table).toHaveTextContent('Aces');
    expect(table).toHaveTextContent('Alice');
    expect(table).toHaveTextContent('Birdies');
    expect(table).toHaveTextContent('Bob');
    expect(table).toHaveTextContent('+$25');
    expect(table).toHaveTextContent('-$25');
    expect(table).toHaveTextContent('$170');
    expect(table).toHaveTextContent('$50');
  });

  it('renders the standings order list', () => {
    render(<WeeklyReportView report={buildReport()} />);
    const standings = screen.getByRole('list');
    const entries = standings.querySelectorAll('li');
    expect(entries).toHaveLength(2);
    expect(entries[0]).toHaveTextContent('Aces');
    expect(entries[1]).toHaveTextContent('Birdies');
  });

  it('omits the multiplier blurb when the tournament is at 1x', () => {
    const report = buildReport({
      tournament: {
        id: 't-1',
        name: 'Regular Event',
        startDate: '2026-02-05',
        endDate: '2026-02-08',
        status: 'completed',
        payoutMultiplier: 1,
        week: '5',
      },
    });
    render(<WeeklyReportView report={report} />);
    expect(screen.queryByText(/payouts/)).not.toBeInTheDocument();
  });
});
