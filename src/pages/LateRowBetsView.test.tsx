import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import LateRowBetsView from './LateRowBetsView';
import type { ReportTeamColumn, WeeklyReport } from '@/api/types';

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    rows: [],
    topTens: 0,
    weeklyTotal: 0,
    previous: 0,
    subtotal: 0,
    topTenCount: 0,
    topTenMoney: 0,
    sideBets: 0,
    totalCash: 0,
    ...overrides,
  };
}

function report(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
  return {
    tournament: {
      id: 't-1',
      name: 'Sample Open',
      startDate: '2026-03-01',
      endDate: '2026-03-04',
      status: 'completed',
      payoutMultiplier: 1,
      week: '9',
    },
    teams: [
      team({ teamId: 't-1', teamName: 'Aces', sideBets: 60 }),
      team({ teamId: 't-2', teamName: 'Birdies', sideBets: -45 }),
      team({ teamId: 't-3', teamName: 'Eagles', sideBets: -15 }),
    ],
    undraftedTopTens: [],
    sideBetDetail: [
      {
        round: 5,
        teams: [
          { teamId: 't-1', golferName: 'Scottie', cumulativeEarnings: 500, payout: 30 },
          { teamId: 't-2', golferName: 'Rory', cumulativeEarnings: 250, payout: -15 },
          { teamId: 't-3', golferName: 'Jon', cumulativeEarnings: 100, payout: -15 },
        ],
      },
      {
        round: 6,
        teams: [
          { teamId: 't-1', golferName: 'Xander', cumulativeEarnings: 300, payout: 30 },
          { teamId: 't-2', golferName: 'Collin', cumulativeEarnings: 200, payout: -15 },
          { teamId: 't-3', golferName: 'Viktor', cumulativeEarnings: 100, payout: -15 },
        ],
      },
    ],
    standingsOrder: [],
    live: false,
    ...overrides,
  };
}

describe('LateRowBetsView', () => {
  it('renders the explainer copy', () => {
    render(<LateRowBetsView report={report()} />);
    expect(
      screen.getByText(/\$15 per team per round\. Highest cumulative earner/i),
    ).toBeInTheDocument();
  });

  it('renders a section per side-bet round', () => {
    render(<LateRowBetsView report={report()} />);
    expect(screen.getByRole('heading', { name: 'Round 5' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Round 6' })).toBeInTheDocument();
  });

  it('resolves team names and sorts entries by cumulative earnings descending', () => {
    render(<LateRowBetsView report={report()} />);
    const round5Heading = screen.getByRole('heading', { name: 'Round 5' });
    const round5Section = round5Heading.parentElement!;
    const rows = within(round5Section).getAllByRole('row').slice(1);
    const teamCells = rows.map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(teamCells).toEqual(['Aces', 'Birdies', 'Eagles']);
  });

  it('renders the totals section sorted by sideBets descending', () => {
    render(<LateRowBetsView report={report()} />);
    const totalsHeading = screen.getByRole('heading', { name: /Total Late Row Bets/i });
    const totalsSection = totalsHeading.parentElement!;
    const rows = within(totalsSection).getAllByRole('row').slice(1);
    const teamCells = rows.map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(teamCells).toEqual(['Aces', 'Eagles', 'Birdies']);
  });

  it('computes the won/lost/net summary from sideBets', () => {
    render(<LateRowBetsView report={report()} />);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent('$60');
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent('-$60');
    expect(screen.getByText('Net').nextSibling).toHaveTextContent('$0');
  });

  it('shows an empty-state message when there are no side bet rounds', () => {
    const empty = report({ sideBetDetail: [] });
    render(<LateRowBetsView report={empty} />);
    expect(screen.getByText(/No late row bets yet/i)).toBeInTheDocument();
  });
});
