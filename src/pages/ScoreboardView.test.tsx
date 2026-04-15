import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import ScoreboardView from './ScoreboardView';
import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/api/types';

function row(overrides: Partial<ReportRow> = {}): ReportRow {
  return {
    round: 1,
    golferName: 'Scottie Scheffler',
    golferId: 'g-1',
    positionStr: 'T1',
    scoreToPar: '-12',
    earnings: 50,
    topTens: 1,
    ownershipPct: 100,
    seasonEarnings: 50,
    seasonTopTens: 1,
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    rows: [row()],
    topTens: 1,
    weeklyTotal: 18,
    previous: 0,
    subtotal: 18,
    topTenCount: 1,
    topTenMoney: 18,
    sideBets: 0,
    totalCash: 18,
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
    teams: [team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 25 })],
    undraftedTopTens: [],
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    ...overrides,
  };
}

describe('ScoreboardView', () => {
  it('renders the tournament name as the heading', () => {
    render(<ScoreboardView report={report()} />);
    expect(screen.getByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
  });

  it('shows the "Results finalized" banner for completed tournaments', () => {
    render(<ScoreboardView report={report()} />);
    expect(screen.getByText(/Results finalized/i)).toBeInTheDocument();
  });

  it('shows the in-progress banner for non-completed tournaments', () => {
    render(
      <ScoreboardView
        report={report({
          tournament: { ...report().tournament, status: 'in_progress' },
        })}
      />,
    );
    expect(screen.getByText(/Tournament in progress/i)).toBeInTheDocument();
  });

  it('sorts team rows by weekly total descending', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 10 }),
            team({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: 30 }),
            team({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: -5 }),
          ],
        })}
      />,
    );
    const tables = screen.getAllByRole('table');
    // first table = teams, second = leaderboard (if any)
    const teamRows = within(tables[0]).getAllByRole('row').slice(1);
    const names = teamRows.map((r) => within(r).getAllByRole('cell')[1]?.textContent);
    expect(names).toEqual(['Birdies', 'Aces', 'Cuts']);
  });

  it('renders the leaderboard with rostered golfers', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              rows: [row({ golferName: 'Tiger', positionStr: 'T3', scoreToPar: '-8' })],
            }),
          ],
          undraftedTopTens: [{ name: 'Phil', position: 5, payout: 0, scoreToPar: 'E' }],
        })}
      />,
    );
    expect(screen.getByRole('heading', { name: /Leaderboard/i })).toBeInTheDocument();
    // Tiger from the rostered team and Phil from undrafted both appear
    const tables = screen.getAllByRole('table');
    const leaderboard = tables[1];
    expect(within(leaderboard).getByText('Tiger')).toBeInTheDocument();
    expect(within(leaderboard).getByText('Phil')).toBeInTheDocument();
    expect(within(leaderboard).getByText('undrafted')).toBeInTheDocument();
  });

  it('hides the leaderboard section when nothing qualifies', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              rows: [row({ golferName: null, positionStr: null, earnings: 0 })],
            }),
          ],
        })}
      />,
    );
    expect(screen.queryByRole('heading', { name: /Leaderboard/i })).not.toBeInTheDocument();
  });
});
