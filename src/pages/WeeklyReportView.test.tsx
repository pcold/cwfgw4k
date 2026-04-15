import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WeeklyReportView from './WeeklyReportView';
import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/api/types';

function row(round: number, overrides: Partial<ReportRow> = {}): ReportRow {
  return {
    round,
    golferName: `Golfer ${round}`,
    golferId: `g-${round}`,
    positionStr: 'T1',
    scoreToPar: '-10',
    earnings: 0,
    topTens: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    rows: [1, 2, 3, 4, 5, 6, 7, 8].map((r) => row(r)),
    topTens: 18,
    weeklyTotal: 25,
    previous: 100,
    subtotal: 125,
    topTenCount: 1,
    topTenMoney: 18,
    sideBets: 45,
    totalCash: 170,
    ...overrides,
  };
}

function report(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
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
    teams: [team()],
    undraftedTopTens: [],
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    ...overrides,
  };
}

describe('WeeklyReportView', () => {
  it('renders the tournament header with payout multiplier', () => {
    render(<WeeklyReportView report={report()} />);
    expect(screen.getByRole('heading', { name: /Players Championship/i })).toBeInTheDocument();
    expect(screen.getByText(/2x payouts/)).toBeInTheDocument();
  });

  it('renders a column header per team', () => {
    render(
      <WeeklyReportView
        report={report({
          teams: [
            team({ teamId: 't-1', teamName: 'Aces' }),
            team({ teamId: 't-2', teamName: 'Birdies' }),
          ],
        })}
      />,
    );
    const headers = screen.getAllByRole('columnheader');
    expect(headers[0]).toHaveTextContent('TEAM #');
    expect(headers[1]).toHaveTextContent('Aces');
    expect(headers[2]).toHaveTextContent('Birdies');
  });

  it('renders 8 draft-round rows with golfer names from team.rows', () => {
    render(<WeeklyReportView report={report()} />);
    expect(screen.getByText('Golfer 1')).toBeInTheDocument();
    expect(screen.getByText('Golfer 8')).toBeInTheDocument();
  });

  it('renders the summary rows (TOP TENS / WEEKLY / PREVIOUS / SUBTOTAL / ROWS 5-6-7-8 / TOTAL CASH)', () => {
    render(<WeeklyReportView report={report()} />);
    expect(screen.getByText('TOP TENS')).toBeInTheDocument();
    expect(screen.getByText('**WEEKLY')).toBeInTheDocument();
    expect(screen.getByText('PREVIOUS')).toBeInTheDocument();
    expect(screen.getByText('SUBTOTAL')).toBeInTheDocument();
    expect(screen.getByText('ROWS 5-6-7-8')).toBeInTheDocument();
    // TOTAL CASH cell content is split by <br> so assert via row contents
    const table = screen.getByRole('table');
    expect(table).toHaveTextContent('TOTALCASH');
  });

  it('flips the header row label when rendering the season total', () => {
    const seasonReport = report({
      tournament: {
        id: null,
        name: 'All Tournaments',
        startDate: null,
        endDate: null,
        status: 'season',
        payoutMultiplier: 1,
        week: null,
      },
    });
    render(<WeeklyReportView report={seasonReport} />);
    expect(screen.getByText('**SEASON')).toBeInTheDocument();
    expect(screen.queryByText('**WEEKLY')).not.toBeInTheDocument();
    expect(screen.getByText(/Season totals/i)).toBeInTheDocument();
  });

  it('shows the LIVE badge when the report is live', () => {
    render(<WeeklyReportView report={report({ live: true })} />);
    expect(screen.getByText(/LIVE — projected standings/i)).toBeInTheDocument();
  });

  it('omits the LIVE badge by default', () => {
    render(<WeeklyReportView report={report()} />);
    expect(screen.queryByText(/LIVE/i)).not.toBeInTheDocument();
  });

  it('renders the undrafted top tens callout when present', () => {
    render(
      <WeeklyReportView
        report={report({
          undraftedTopTens: [
            { name: 'Lefty', position: 5, payout: 8, scoreToPar: '-3' },
          ],
        })}
      />,
    );
    expect(screen.getByText(/UNDRAFTED TOP TENS/)).toBeInTheDocument();
    expect(screen.getByText(/Lefty/)).toBeInTheDocument();
  });

  it('renders an em dash for empty draft slots', () => {
    const withMissing = report({
      teams: [
        team({
          rows: [1, 2, 3, 4, 5, 6, 7, 8].map((r) =>
            r === 3 ? row(r, { golferName: null }) : row(r),
          ),
        }),
      ],
    });
    render(<WeeklyReportView report={withMissing} />);
    const rows = screen.getAllByRole('row');
    // rows[0] = team header, rows[1..8] = rounds 1..8
    const round3 = rows[3];
    expect(within(round3).getByText('—')).toBeInTheDocument();
  });

  it('computes and displays the won/lost/net summary', () => {
    const tworeport = report({
      teams: [
        team({ teamId: 't-1', totalCash: 120 }),
        team({ teamId: 't-2', teamName: 'Birdies', totalCash: -80 }),
      ],
    });
    render(<WeeklyReportView report={tworeport} />);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent('$120');
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent('-$80');
    expect(screen.getByText('Net').nextSibling).toHaveTextContent('$40');
  });

  it('makes round-cell golfer names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    render(
      <WeeklyReportView
        report={report({
          teams: [team({ rows: [row(1, { golferId: 'g-99', golferName: 'Clickable' })] })],
        })}
        onGolferClick={onGolferClick}
      />,
    );
    await user.click(screen.getByRole('button', { name: /Clickable/ }));
    expect(onGolferClick).toHaveBeenCalledWith('g-99');
  });

  it('renders golfer names as plain text when no onGolferClick is provided', () => {
    render(
      <WeeklyReportView
        report={report({
          teams: [team({ rows: [row(1, { golferId: 'g-1', golferName: 'NotClickable' })] })],
        })}
      />,
    );
    expect(screen.queryByRole('button', { name: /NotClickable/ })).not.toBeInTheDocument();
    expect(screen.getByText(/NotClickable/)).toBeInTheDocument();
  });
});
