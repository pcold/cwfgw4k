import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    pairKey: null,
    ...overrides,
  };
}

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
    ownerName: 'Alice',
    rows: [row()],
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
    liveLeaderboard: [],
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
          undraftedTopTens: [{ name: 'Phil', position: 5, payout: 0, scoreToPar: 'E', pairKey: null }],
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

  it('makes rostered golfer names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              // positionStr null so the leaderboard ignores this row and we only see the name once
              rows: [row({ golferId: 'g-42', golferName: 'TeamGolfer', positionStr: null })],
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
              rows: [row({ golferId: 'g-1', golferName: 'PlainGolfer', positionStr: null })],
            }),
          ],
        })}
      />,
    );
    expect(screen.queryByRole('button', { name: 'PlainGolfer' })).not.toBeInTheDocument();
    expect(screen.getByText('PlainGolfer')).toBeInTheDocument();
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

  it('filters the leaderboard to rostered golfers on the selected team', async () => {
    const user = userEvent.setup();
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamId: 't-1',
              teamName: 'Aces',
              rows: [row({ golferName: 'Tiger', positionStr: 'T3', scoreToPar: '-8' })],
            }),
            team({
              teamId: 't-2',
              teamName: 'Birdies',
              rows: [
                row({
                  golferId: 'g-2',
                  golferName: 'Rory',
                  positionStr: 'T5',
                  scoreToPar: '-6',
                }),
              ],
            }),
          ],
          undraftedTopTens: [{ name: 'Phil', position: 5, payout: 0, scoreToPar: 'E', pairKey: null }],
        })}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Aces');

    const leaderboard = screen.getAllByRole('table')[1];
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Tiger')).toBeInTheDocument();
  });

  it('offers Undrafted on the leaderboard filter when undrafted golfers are present', async () => {
    const user = userEvent.setup();
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              rows: [row({ golferName: 'Tiger', positionStr: 'T3', scoreToPar: '-8' })],
            }),
          ],
          undraftedTopTens: [{ name: 'Phil', position: 5, payout: 0, scoreToPar: 'E', pairKey: null }],
        })}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Undrafted');

    const leaderboard = screen.getAllByRole('table')[1];
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Phil')).toBeInTheDocument();
  });

  it('omits the T prefix on a solo position in the Golfers in Top 10 column', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              rows: [
                row({
                  golferId: 'g-1',
                  golferName: 'FITZPATRICK',
                  positionStr: '1',
                  scoreToPar: '-19',
                  earnings: 18,
                }),
              ],
            }),
          ],
        })}
      />,
    );

    const teamTable = screen.getAllByRole('table')[0];
    const teamCells = within(teamTable).getAllByRole('cell');
    const golferCell = teamCells[teamCells.length - 1];
    // Visible text should read "FITZPATRICK 1 $18" — no stray T.
    expect(golferCell.textContent ?? '').not.toMatch(/T\s*1\b/);
    expect(golferCell).toHaveTextContent('1');
  });

  it('shows the T prefix on a tied position in the Golfers in Top 10 column', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              rows: [
                row({
                  golferId: 'g-1',
                  golferName: 'MORIKAWA',
                  positionStr: 'T4',
                  scoreToPar: '-13',
                  earnings: 7,
                }),
              ],
            }),
          ],
        })}
      />,
    );

    const teamTable = screen.getAllByRole('table')[0];
    const teamCells = within(teamTable).getAllByRole('cell');
    const golferCell = teamCells[teamCells.length - 1];
    expect(golferCell.textContent ?? '').toMatch(/T\s*4/);
  });

  it('shows a placeholder row and keeps the leaderboard filter reachable when it yields zero rows', async () => {
    const user = userEvent.setup();
    render(
      <ScoreboardView
        report={report({
          teams: [
            team({
              teamName: 'Aces',
              rows: [row({ golferName: 'Tiger', positionStr: 'T3', scoreToPar: '-8' })],
            }),
          ],
          // No undrafted golfers — Undrafted option is not offered.
        })}
      />,
    );

    // Narrow to Aces first, then switch back to Team to show a populated table again.
    const select = screen.getByLabelText(/Filter leaderboard by team/i);
    await user.selectOptions(select, 'Aces');
    await user.selectOptions(select, 'Team');

    const leaderboard = screen.getAllByRole('table')[1];
    expect(within(leaderboard).getByText('Tiger')).toBeInTheDocument();
  });

  it('renders pair entries as one row with slash-joined names and teams', () => {
    render(
      <ScoreboardView
        report={report({
          teams: [],
          undraftedTopTens: [],
          liveLeaderboard: [
            { name: 'McIlroy', position: 1, scoreToPar: '-28', rostered: false, teamName: null, pairKey: 'team:99' },
            { name: 'Lowry', position: 1, scoreToPar: '-28', rostered: true, teamName: 'PAYRAY', pairKey: 'team:99' },
          ],
        })}
      />,
    );

    const leaderboard = screen.getAllByRole('table')[1];
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('McIlroy / Lowry')).toBeInTheDocument();
    expect(within(rows[0]).getByText('undrafted / PAYRAY')).toBeInTheDocument();
  });

  it('filter by a single team name matches pair rows where that team rostered one partner', async () => {
    const user = userEvent.setup();
    render(
      <ScoreboardView
        report={report({
          teams: [],
          liveLeaderboard: [
            { name: 'McIlroy', position: 1, scoreToPar: '-28', rostered: true, teamName: 'ROSEH2O', pairKey: 'team:1' },
            { name: 'Lowry', position: 1, scoreToPar: '-28', rostered: true, teamName: 'PAYRAY', pairKey: 'team:1' },
            { name: 'Scheffler', position: 3, scoreToPar: '-25', rostered: false, teamName: null, pairKey: null },
          ],
        })}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'PAYRAY');
    const leaderboard = screen.getAllByRole('table')[1];
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('McIlroy / Lowry')).toBeInTheDocument();
  });
});
