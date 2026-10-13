import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import LateRowBetsView from './LateRowBetsView';
import type {
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  SideBetOutcome,
  WeeklyReport,
} from '@/shared/api/types';

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    cells: [],
    topTenEarnings: 0,
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

function entry(
  teamId: string,
  teamName: string,
  golferName: string,
  cumulativeEarnings: number,
  payout: number,
  outcome: SideBetOutcome | null,
): ReportSideBetTeamEntry {
  return { teamId, teamName, golferName, cumulativeEarnings, payout, outcome };
}

function report(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
  return {
    scope: 'tournament',
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
      team({ teamId: 't-1', teamName: 'Aces', sideBets: 80 }),
      team({ teamId: 't-2', teamName: 'Birdies', sideBets: -60 }),
      team({ teamId: 't-3', teamName: 'Eagles', sideBets: -20 }),
    ],
    undraftedTopTens: [],
    sideBetRules: { amountPerTeam: 20, rounds: [2, 3] },
    sideBetDetail: [
      {
        round: 2,
        teams: [
          entry('t-1', 'Aces', 'Scottie', 500, 40, 'won'),
          entry('t-2', 'Birdies', 'Rory', 250, -20, 'lost'),
          entry('t-3', 'Eagles', 'Jon', 100, -20, 'lost'),
        ],
      },
      {
        round: 3,
        teams: [
          entry('t-1', 'Aces', 'Xander', 300, 40, 'won'),
          entry('t-2', 'Birdies', 'Collin', 200, -20, 'lost'),
          entry('t-3', 'Eagles', 'Viktor', 100, -20, 'lost'),
        ],
      },
    ],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    ...overrides,
  };
}

describe('LateRowBetsView', () => {
  it("states the season's own amount and rounds in the rules sentence", () => {
    render(<LateRowBetsView report={report()} />);
    expect(
      screen.getByText(
        "$20.00 per team in each of draft rounds 2 and 3. Every team behind a round's highest " +
          'cumulative earner pays $20.00; teams tied for the highest split what was paid.',
      ),
    ).toBeInTheDocument();
  });

  it('names a single side-bet round in the singular', () => {
    render(
      <LateRowBetsView report={report({ sideBetRules: { amountPerTeam: 12.5, rounds: [7] } })} />,
    );
    expect(screen.getByText(/^\$12\.50 per team in draft round 7\./)).toBeInTheDocument();
  });

  it('renders a section per side-bet round', () => {
    render(<LateRowBetsView report={report()} />);
    expect(screen.getByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Round 3' })).toBeInTheDocument();
  });

  it("shows each entry's team name from the entry itself, sorted by cumulative earnings", () => {
    render(<LateRowBetsView report={report({ teams: [] })} />);
    const round2Section = screen.getByRole('heading', { name: 'Round 2' }).parentElement;
    if (!round2Section) throw new Error('the Round 2 heading has no section around it');
    const rows = within(round2Section).getAllByRole('row').slice(1);
    const teamCells = rows.map((r) => within(r).getAllByRole('cell')[0]?.textContent);
    expect(teamCells).toEqual(['Aces', 'Birdies', 'Eagles']);
  });

  it('renders the totals section sorted by sideBets descending', () => {
    render(<LateRowBetsView report={report()} />);
    const totalsHeading = screen.getByRole('heading', { name: /Total Late Row Bets/i });
    const totalsSection = totalsHeading.parentElement;
    if (!totalsSection) throw new Error('the totals heading has no section around it');
    const rows = within(totalsSection).getAllByRole('row').slice(1);
    const teamCells = rows.map((r) => within(r).getAllByRole('cell')[0]?.textContent);
    expect(teamCells).toEqual(['Aces', 'Eagles', 'Birdies']);
  });

  it('computes the won/lost/net summary from sideBets', () => {
    render(<LateRowBetsView report={report()} />);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent('$80');
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent('-$80');
    expect(screen.getByText('Net').nextSibling).toHaveTextContent('$0');
  });

  it('says a season that stakes nothing on side bets has none, and shows no rules or totals', () => {
    const noSideBets = report({ sideBetRules: null, sideBetDetail: [] });
    render(<LateRowBetsView report={noSideBets} />);
    expect(screen.getByText('This season has no late row bets.')).toBeInTheDocument();
    expect(screen.queryByText(/per team in/)).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Total Late Row Bets/i })).not.toBeInTheDocument();
  });
});
