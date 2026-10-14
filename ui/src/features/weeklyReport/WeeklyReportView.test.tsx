import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WeeklyReportView from './WeeklyReportView';
import type {
  ReportCell,
  ReportSideBetRules,
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  SideBetOutcome,
  WeeklyReport,
} from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { makeWonLostTotals } from '@/shared/test/fixtures';

function row(round: number, overrides: Partial<ReportCell> = {}): ReportCell {
  return {
    round,
    golferName: `Golfer ${round}`,
    golferId: `g-${round}`,
    positionStr: 'T1',
    finish: { position: 1, tied: true },
    scoreToPar: -10,
    earnings: 0,
    topTens: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
    pairKey: null,
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    cells: [1, 2, 3, 4, 5, 6, 7, 8].map((r) => row(r)),
    topTenEarnings: 18,
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
    scope: 'tournament',
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
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    ...overrides,
  };
}

const TWENTY_ON_ROUNDS_TWO_AND_THREE: ReportSideBetRules = { amountPerTeam: 20, rounds: [2, 3] };

function sideBetEntry(
  teamId: string,
  payout: number,
  outcome: SideBetOutcome | null,
): ReportSideBetTeamEntry {
  return { teamId, teamName: teamId, golferName: 'Golfer', cumulativeEarnings: 0, payout, outcome };
}

function roundRow(round: number): HTMLElement {
  const found = screen
    .getAllByRole('row')
    .find((r) => within(r).queryAllByRole('cell')[0]?.textContent?.startsWith(`${round}`));
  if (!found) throw new Error(`no row for round ${round}`);
  return found;
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

  it('renders 8 draft-round rows with golfer names from team.cells', () => {
    render(<WeeklyReportView report={report()} />);
    expect(screen.getByText('Golfer 1')).toBeInTheDocument();
    expect(screen.getByText('Golfer 8')).toBeInTheDocument();
  });

  it('renders the summary rows (TOP TENS / WEEKLY / PREVIOUS / SUBTOTAL / side bets / TOTAL CASH)', () => {
    render(<WeeklyReportView report={report({ sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE })} />);
    expect(screen.getByText('TOP TENS')).toBeInTheDocument();
    expect(screen.getByText('**WEEKLY')).toBeInTheDocument();
    expect(screen.getByText('PREVIOUS')).toBeInTheDocument();
    expect(screen.getByText('SUBTOTAL')).toBeInTheDocument();
    expect(screen.getByText('ROWS 2-3')).toBeInTheDocument();
    // TOTAL CASH cell content is split by <br> so assert via row contents
    const table = screen.getByRole('table');
    expect(table).toHaveTextContent('TOTALCASH');
  });

  it("shows the season's own side-bet amount beside its rounds and beside no other", () => {
    render(<WeeklyReportView report={report({ sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE })} />);
    expect(screen.getAllByText('($20.00)')).toHaveLength(2);
    expect(within(roundRow(2)).getByText('($20.00)')).toBeInTheDocument();
    expect(within(roundRow(3)).getByText('($20.00)')).toBeInTheDocument();
    expect(within(roundRow(5)).queryByText(/\(\$/)).not.toBeInTheDocument();
  });

  it('shows no amount beside any round and a totals row naming none for a season without side bets', () => {
    render(<WeeklyReportView report={report({ sideBetRules: null, sideBetDetail: [] })} />);
    expect(screen.queryByText(/^\(\$/)).not.toBeInTheDocument();
    expect(screen.getByText('SIDE BETS')).toBeInTheDocument();
    expect(screen.queryByText(/^ROWS/)).not.toBeInTheDocument();
  });

  it('highlights the cell of every team the server marked a winner, a round they all tie included', () => {
    const tiedRoundTwo = report({
      teams: [team({ teamId: 't-1' }), team({ teamId: 't-2', teamName: 'Birdies' })],
      sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE,
      sideBetDetail: [
        { round: 2, teams: [sideBetEntry('t-1', 0, 'won'), sideBetEntry('t-2', 0, 'won')] },
        { round: 3, teams: [sideBetEntry('t-1', 20, 'won'), sideBetEntry('t-2', -20, 'lost')] },
      ],
    });
    render(<WeeklyReportView report={tiedRoundTwo} />);

    const [, acesTwo, birdiesTwo] = within(roundRow(2)).getAllByRole('cell');
    expect(acesTwo).toHaveClass('bg-red-800');
    expect(birdiesTwo).toHaveClass('bg-red-800');
    const [, acesThree, birdiesThree] = within(roundRow(3)).getAllByRole('cell');
    expect(acesThree).toHaveClass('bg-red-800');
    expect(birdiesThree).not.toHaveClass('bg-red-800');
  });

  it('flips the header row label when rendering the season total', () => {
    const seasonReport = report({
      scope: 'season',
      tournament: {
        id: null,
        name: 'All Tournaments',
        startDate: null,
        endDate: null,
        status: null,
        payoutMultiplier: 1,
        week: null,
      },
    });
    render(<WeeklyReportView report={seasonReport} />);
    expect(screen.getByText('**SEASON')).toBeInTheDocument();
    expect(screen.queryByText('**WEEKLY')).not.toBeInTheDocument();
    expect(screen.getByText(/Season totals/i)).toBeInTheDocument();
  });

  it('shows the season-to-date line under each cell only on a tournament report', () => {
    const teams = [team({ cells: [row(1, { seasonEarnings: 30, seasonTopTens: 2 })] })];

    const { unmount } = render(<WeeklyReportView report={report({ teams })} />);
    expect(screen.getByText(/\(2\)/)).toBeInTheDocument();
    unmount();

    render(<WeeklyReportView report={report({ scope: 'season', teams })} />);
    expect(screen.queryByText(/\(2\)/)).not.toBeInTheDocument();
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
            { name: 'Lefty', position: 5, payout: 8, scoreToPar: -3, pairKey: null },
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
          cells: [1, 2, 3, 4, 5, 6, 7, 8].map((r) =>
            r === 3 ? row(r, { golferName: null }) : row(r),
          ),
        }),
      ],
    });
    render(<WeeklyReportView report={withMissing} />);
    const rows = screen.getAllByRole('row');
    // rows[0] = team header, rows[1..8] = rounds 1..8
    const round3 = nth(rows, 3);
    expect(within(round3).getByText('—')).toBeInTheDocument();
  });

  it("displays the report's cash totals, which are not a sum over its teams", () => {
    const disagreeing = report({
      teams: [
        team({ teamId: 't-1', totalCash: 120 }),
        team({ teamId: 't-2', teamName: 'Birdies', totalCash: -80 }),
      ],
      cashTotals: { won: 9, lost: -2, net: 7 },
      sideBetTotals: { won: 55, lost: -66, net: -11 },
    });
    render(<WeeklyReportView report={disagreeing} />);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent('$9.00');
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent('-$2.00');
    expect(screen.getByText('Net').nextSibling).toHaveTextContent('$7.00');
  });

  it('makes round-cell golfer names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    render(
      <WeeklyReportView
        report={report({
          teams: [team({ cells: [row(1, { golferId: 'g-99', golferName: 'Clickable' })] })],
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
          teams: [team({ cells: [row(1, { golferId: 'g-1', golferName: 'NotClickable' })] })],
        })}
      />,
    );
    expect(screen.queryByRole('button', { name: /NotClickable/ })).not.toBeInTheDocument();
    expect(screen.getByText(/NotClickable/)).toBeInTheDocument();
  });
});
