import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReportCell, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { makeLeaderboardEntry, makeWonLostTotals } from '@/shared/test/fixtures';
import ScoreboardView from './ScoreboardView';

function row(overrides: Partial<ReportCell> = {}): ReportCell {
  return {
    round: 1,
    golferName: 'Scottie Scheffler',
    golferId: 'g-1',
    positionStr: 'T1',
    finish: { position: 1, tied: true },
    scoreToPar: -12,
    earnings: 50,
    topTens: 1,
    ownershipPct: 100,
    seasonEarnings: 50,
    seasonTopTens: 1,
    pairKey: null,
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    cells: [row()],
    topTenEarnings: 1,
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

const tiger = makeLeaderboardEntry({
  position: 3,
  scoreToPar: -8,
  players: [{ name: 'Tiger', teamNames: ['Aces'] }],
});
const rory = makeLeaderboardEntry({
  position: 5,
  scoreToPar: -6,
  players: [{ name: 'Rory', teamNames: ['Birdies'] }],
});
const phil = makeLeaderboardEntry({ position: 5, players: [{ name: 'Phil', teamNames: [] }] });
const mcIlroyAndLowry = makeLeaderboardEntry({
  scoreToPar: -28,
  players: [
    { name: 'McIlroy', teamNames: [] },
    { name: 'Lowry', teamNames: ['PAYRAY'] },
  ],
});

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
    teams: [team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 25 })],
    undraftedTopTens: [],
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
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

  it('renders a finalize slot inside the in-progress banner when provided', () => {
    render(
      <ScoreboardView
        report={report({
          tournament: { ...report().tournament, status: 'in_progress' },
        })}
        finalizeSlot={<button type="button">Finalize Results</button>}
      />,
    );
    expect(screen.getByRole('button', { name: /Finalize Results/i })).toBeInTheDocument();
  });

  it('does not render the finalize slot for completed tournaments', () => {
    render(
      <ScoreboardView
        report={report()}
        finalizeSlot={<button type="button">Finalize Results</button>}
      />,
    );
    expect(screen.queryByRole('button', { name: /Finalize Results/i })).not.toBeInTheDocument();
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
    const teamRows = within(nth(tables, 0)).getAllByRole('row').slice(1);
    const names = teamRows.map((r) => within(r).getAllByRole('cell')[1]?.textContent);
    expect(names).toEqual(['Birdies', 'Aces', 'Cuts']);
  });

  it('renders the leaderboard the server sent, rostered and undrafted alike', () => {
    render(<ScoreboardView report={report({ liveLeaderboard: [tiger, phil] })} />);
    expect(screen.getByRole('heading', { name: /Leaderboard/i })).toBeInTheDocument();
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell').slice(0, 4).map((c) => c.textContent))).toEqual([
      ['3', 'Tiger', 'Aces', '-8'],
      ['5', 'Phil', 'undrafted', 'E'],
    ]);
  });

  it('keeps the server\'s leaderboard order rather than sorting by position', () => {
    render(<ScoreboardView report={report({ liveLeaderboard: [phil, tiger] })} />);
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell')[1]?.textContent)).toEqual(['Phil', 'Tiger']);
  });

  it('prints round scores and the stroke total, and a dash for a round not yet played', () => {
    const leader = makeLeaderboardEntry({ scoreToPar: null, roundScores: [64, 68], totalStrokes: 132 });
    render(<ScoreboardView report={report({ liveLeaderboard: [leader] })} />);
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const cells = within(nth(within(leaderboard).getAllByRole('row'), 1)).getAllByRole('cell');
    expect(cells.slice(3).map((c) => c.textContent)).toEqual(['—', '64', '68', '—', '—', '132']);
  });

  it('shows the won, lost and net the server totalled, not a sum of the team rows', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [team({ weeklyTotal: 25 })],
          weeklyTotals: { won: 55, lost: -66, net: -11 },
        })}
      />,
    );
    expect(screen.getByText('Won').parentElement).toHaveTextContent('$55');
    expect(screen.getByText('Lost').parentElement).toHaveTextContent('-$66');
    expect(screen.getByText('Net').parentElement).toHaveTextContent('-$11');
  });

  it('makes rostered golfer names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              cells: [row({ golferId: 'g-42', golferName: 'TeamGolfer' })],
            }),
          ],
        })}
        onGolferClick={onGolferClick}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'TeamGolfer' }));
    expect(onGolferClick).toHaveBeenCalledWith('g-42');
  });

  it('renders golfer names as plain text when no onGolferClick is provided', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              cells: [row({ golferId: 'g-1', golferName: 'PlainGolfer' })],
            }),
          ],
        })}
      />,
    );
    expect(screen.queryByRole('button', { name: 'PlainGolfer' })).not.toBeInTheDocument();
    expect(screen.getByText('PlainGolfer')).toBeInTheDocument();
  });

  it('hides the leaderboard section when the server sent no rows, whatever the cells hold', () => {
    render(<ScoreboardView report={report({ liveLeaderboard: [] })} />);
    expect(screen.queryByRole('heading', { name: /Leaderboard/i })).not.toBeInTheDocument();
  });

  it('filters the leaderboard to rostered golfers on the selected team', async () => {
    const user = userEvent.setup();
    render(<ScoreboardView report={report({ liveLeaderboard: [tiger, rory, phil] })} />);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Aces');

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Tiger')).toBeInTheDocument();
  });

  it('offers Undrafted on the leaderboard filter when undrafted golfers are present', async () => {
    const user = userEvent.setup();
    render(<ScoreboardView report={report({ liveLeaderboard: [tiger, phil] })} />);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Undrafted');

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Phil')).toBeInTheDocument();
  });

  it('offers no Undrafted option when every leaderboard player is rostered', () => {
    render(<ScoreboardView report={report({ liveLeaderboard: [tiger, rory] })} />);
    const options = within(screen.getByLabelText(/Filter leaderboard by team/i)).getAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual(['Team', 'Aces', 'Birdies']);
  });

  it('omits the T prefix on a solo position in the Golfers in Top 10 column', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              cells: [
                row({
                  golferId: 'g-1',
                  golferName: 'FITZPATRICK',
                  positionStr: '1',
                  finish: { position: 1, tied: false },
                  scoreToPar: -19,
                  earnings: 18,
                }),
              ],
            }),
          ],
        })}
      />,
    );

    const teamTable = nth(screen.getAllByRole('table'), 0);
    const teamCells = within(teamTable).getAllByRole('cell');
    const golferCell = nth(teamCells, teamCells.length - 1);
    // Visible text should read "FITZPATRICK 1 $18" — no stray T.
    expect(golferCell.textContent).not.toMatch(/T\s*1\b/);
    expect(golferCell).toHaveTextContent('1');
  });

  it('shows the T prefix on a tied position in the Golfers in Top 10 column', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              cells: [
                row({
                  golferId: 'g-1',
                  golferName: 'MORIKAWA',
                  positionStr: 'T4',
                  finish: { position: 4, tied: true },
                  scoreToPar: -13,
                  earnings: 7,
                }),
              ],
            }),
          ],
        })}
      />,
    );

    const teamTable = nth(screen.getAllByRole('table'), 0);
    const teamCells = within(teamTable).getAllByRole('cell');
    const golferCell = nth(teamCells, teamCells.length - 1);
    expect(golferCell.textContent).toMatch(/T\s*4/);
  });

  it('shows the whole leaderboard again when the filter is set back to Team', async () => {
    const user = userEvent.setup();
    render(<ScoreboardView report={report({ liveLeaderboard: [tiger, rory] })} />);

    const select = screen.getByLabelText(/Filter leaderboard by team/i);
    await user.selectOptions(select, 'Aces');
    await user.selectOptions(select, 'Team');

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    expect(within(leaderboard).getAllByRole('row').slice(1)).toHaveLength(2);
  });

  it('renders a pair as one row with slash-joined names and teams', () => {
    render(<ScoreboardView report={report({ liveLeaderboard: [mcIlroyAndLowry] })} />);

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('McIlroy / Lowry')).toBeInTheDocument();
    expect(within(nth(rows, 0)).getByText('undrafted / PAYRAY')).toBeInTheDocument();
  });

  it('filter by a single team name matches pair rows where that team rostered one partner', async () => {
    const user = userEvent.setup();
    render(<ScoreboardView report={report({ liveLeaderboard: [mcIlroyAndLowry, phil] })} />);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'PAYRAY');
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('McIlroy / Lowry')).toBeInTheDocument();
  });

  it('tints a rostered row and its team label, and leaves an undrafted pair plain', () => {
    const undraftedPair = makeLeaderboardEntry({
      position: 2,
      players: [
        { name: 'Novak', teamNames: [] },
        { name: 'Griffin', teamNames: [] },
      ],
    });
    render(<ScoreboardView report={report({ liveLeaderboard: [tiger, undraftedPair] })} />);

    expect(screen.getByText('Tiger').closest('tr')).toHaveClass('bg-green-900/10');
    expect(screen.getByText('undrafted / undrafted')).toHaveClass('text-gray-600');
    expect(screen.getByText('undrafted / undrafted').closest('tr')).not.toHaveClass('bg-green-900/10');
  });
});
