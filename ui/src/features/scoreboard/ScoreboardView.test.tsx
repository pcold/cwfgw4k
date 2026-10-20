import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReportCell, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { makeLeaderboardEntry, makeWonLostTotals } from '@/shared/test/fixtures';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ScoreboardView from './ScoreboardView';

function row(overrides: Partial<ReportCell> = {}): ReportCell {
  return {
    round: 1,
    golferName: 'Scottie Scheffler',
    golferId: 'g-1',
    positionStr: 'T1',
    finish: { position: 1, tied: true },
    earnings: 50,
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

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

function report(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
  const teams = overrides.teams ?? [team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 25 })];
  return {
    scope: 'tournament',
    tournament: {
      name: 'Sample Open',
      startDate: '2026-03-01',
      endDate: '2026-03-04',
      status: 'completed',
      payoutMultiplier: 1,
      week: '9',
    },
    teams,
    undraftedTopTens: [],
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveEligible: false,
    awaitingTeeOff: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: teams.map((column) => column.teamId),
    ...overrides,
  };
}

function teamTable(): HTMLElement {
  return nth(screen.getAllByRole('table'), 0);
}

function phoneCards(): HTMLElement[] {
  return within(screen.getByRole('list', { name: 'Weekly results' })).getAllByRole('listitem');
}

function phoneCard(teamName: string): HTMLElement {
  const card = within(screen.getByRole('list', { name: 'Weekly results' })).getByText(teamName).closest('li');
  if (!card) throw new Error(`no phone card for ${teamName}`);
  return card;
}

function phoneDetailLine(teamName: string): HTMLElement {
  const line = within(phoneCard(teamName)).getByText(teamName).nextElementSibling;
  if (!(line instanceof HTMLElement)) throw new Error(`no detail line for ${teamName}`);
  return line;
}

function teamCell(teamName: string): HTMLElement {
  const cell = within(teamTable()).getByText(teamName).closest('td');
  if (!cell) throw new Error(`no team cell for ${teamName}`);
  return cell;
}

function golferLine(teamName: string): HTMLElement {
  const line = teamCell(teamName).children[1];
  if (!(line instanceof HTMLElement)) throw new Error(`no golfer line for ${teamName}`);
  return line;
}

function teamChips(leaderboardRow: HTMLElement): Element[] {
  const chips = nth(within(leaderboardRow).getAllByRole('cell'), 2).firstElementChild;
  return chips ? Array.from(chips.children) : [];
}

describe('ScoreboardView', () => {
  it('shows the "Results finalized" banner for completed tournaments', () => {
    renderWithProviders(<ScoreboardView report={report()} />, NO_PROVIDERS);
    expect(screen.getByText(/Results finalized/i)).toBeInTheDocument();
  });

  it('shows the in-progress banner for non-completed tournaments', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          tournament: { ...report().tournament, status: 'in_progress' },
        })}
      />,
      NO_PROVIDERS,
    );
    expect(screen.getByText(/Tournament in progress/i)).toBeInTheDocument();
  });

  it('says play has not started in a neutral banner, still holding the slot, while awaiting tee-off', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          tournament: { ...report().tournament, status: 'in_progress' },
          awaitingTeeOff: true,
        })}
        finalizeSlot={<button type="button">Manage player links</button>}
      />,
      NO_PROVIDERS,
    );
    const banner = screen.getByText("Play hasn't started — no scores yet");
    expect(banner.parentElement).not.toHaveClass('bg-semantic-warning-tint/40');
    expect(screen.queryByText(/Tournament in progress/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Manage player links/i })).toBeInTheDocument();
  });

  it('renders a finalize slot inside the in-progress banner when provided', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          tournament: { ...report().tournament, status: 'in_progress' },
        })}
        finalizeSlot={<button type="button">Finalize Results</button>}
      />,
      NO_PROVIDERS,
    );
    expect(screen.getByRole('button', { name: /Finalize Results/i })).toBeInTheDocument();
  });

  it('does not render the finalize slot for completed tournaments', () => {
    renderWithProviders(
      <ScoreboardView
        report={report()}
        finalizeSlot={<button type="button">Finalize Results</button>}
      />,
      NO_PROVIDERS,
    );
    expect(screen.queryByRole('button', { name: /Finalize Results/i })).not.toBeInTheDocument();
  });

  it('numbers the team rows in the server\'s weekly standings, even against their weekly totals', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          teams: [
            team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 10, cells: [] }),
            team({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: 30, cells: [] }),
            team({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: -5, cells: [] }),
          ],
          weeklyStandings: ['t-3', 't-1', 't-2'],
        })}
      />,
      NO_PROVIDERS,
    );
    const teamRows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    const placings = teamRows.map((r) => within(r).getAllByRole('cell').slice(0, 2).map((c) => c.textContent));
    expect(placings).toEqual([
      ['1', 'Cuts'],
      ['2', 'Aces'],
      ['3', 'Birdies'],
    ]);
  });

  it('renders the leaderboard the server sent, rostered and undrafted alike', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger, phil] })} />, NO_PROVIDERS);
    expect(screen.getByRole('heading', { name: /Leaderboard/i })).toBeInTheDocument();
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell').slice(0, 4).map((c) => c.textContent))).toEqual([
      ['3', 'Tiger', 'Aces', '-8'],
      ['5', 'Phil', 'Undrafted', 'E'],
    ]);
  });

  it('keeps the server\'s leaderboard order rather than sorting by position', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [phil, tiger] })} />, NO_PROVIDERS);
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell')[1]?.textContent)).toEqual(['Phil', 'Tiger']);
  });

  it('prints a dash for the position and score of a slot yet to tee off', () => {
    const lateWave = makeLeaderboardEntry({ position: null, scoreToPar: null });
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [lateWave] })} />, NO_PROVIDERS);
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const cells = within(nth(within(leaderboard).getAllByRole('row'), 1)).getAllByRole('cell');
    expect([nth(cells, 0), nth(cells, 3)].map((c) => c.textContent)).toEqual(['—', '—']);
  });

  it('prints round scores and the stroke total, and a dash for a round not yet played', () => {
    const leader = makeLeaderboardEntry({ scoreToPar: null, roundScores: [64, 68], totalStrokes: 132 });
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [leader] })} />, NO_PROVIDERS);
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const cells = within(nth(within(leaderboard).getAllByRole('row'), 1)).getAllByRole('cell');
    expect(cells.slice(3).map((c) => c.textContent)).toEqual(['—', '64', '68', '—', '—', '132']);
  });

  it('shows the won, lost and net the server totalled, not a sum of the team rows', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          teams: [team({ weeklyTotal: 25 })],
          weeklyTotals: { won: 55, lost: -66, net: -11 },
        })}
      />,
      NO_PROVIDERS,
    );
    expect(screen.getByText('Won').parentElement).toHaveTextContent('+$55.00');
    expect(screen.getByText('Lost').parentElement).toHaveTextContent('\u2212$66.00');
    expect(screen.getByText('Net').parentElement).toHaveTextContent('\u2212$11.00');
  });

  it('makes rostered golfer names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
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
      NO_PROVIDERS,
    );
    await user.click(within(teamTable()).getByRole('button', { name: 'TeamGolfer' }));
    expect(onGolferClick).toHaveBeenCalledWith('g-42');
  });

  it('gives each golfer name the tap-target height, however many share the cell', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          teams: [
            team({
              cells: [
                row({ golferId: 'g-1', golferName: 'FirstGolfer' }),
                row({ round: 2, golferId: 'g-2', golferName: 'SecondGolfer' }),
              ],
            }),
          ],
        })}
        onGolferClick={vi.fn()}
      />,
      NO_PROVIDERS,
    );

    expect(within(teamTable()).getByRole('button', { name: 'FirstGolfer' })).toHaveClass(TAP_TARGET_CLASS);
    expect(within(teamTable()).getByRole('button', { name: 'SecondGolfer' })).toHaveClass(TAP_TARGET_CLASS);
  });

  it('renders golfer names as plain text when no onGolferClick is provided', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          teams: [
            team({
              cells: [row({ golferId: 'g-1', golferName: 'PlainGolfer' })],
            }),
          ],
        })}
      />,
      NO_PROVIDERS,
    );
    expect(screen.queryByRole('button', { name: 'PlainGolfer' })).not.toBeInTheDocument();
    expect(within(teamTable()).getByText('PlainGolfer')).toBeInTheDocument();
  });

  it('hides the leaderboard section when the server sent no rows, whatever the cells hold', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [] })} />, NO_PROVIDERS);
    expect(screen.queryByRole('heading', { name: /Leaderboard/i })).not.toBeInTheDocument();
    expect(screen.getByRole('table').closest('.grid')).toBeNull();
  });

  it('filters the leaderboard to rostered golfers on the selected team', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger, rory, phil] })} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Aces');

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Tiger')).toBeInTheDocument();
  });

  it('offers Undrafted on the leaderboard filter when undrafted golfers are present', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger, phil] })} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Undrafted');

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Phil')).toBeInTheDocument();
  });

  it('offers no Undrafted option when every leaderboard player is rostered', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger, rory] })} />, NO_PROVIDERS);
    const options = within(screen.getByLabelText(/Filter leaderboard by team/i)).getAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual(['Team', 'Aces', 'Birdies']);
  });

  it('omits the T prefix on a solo position in the golfer line', () => {
    renderWithProviders(
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
                  earnings: 18,
                }),
              ],
            }),
          ],
        })}
      />,
      NO_PROVIDERS,
    );

    expect(golferLine('Aces')).toHaveTextContent(/^FITZPATRICK 1 \+\$18\.00$/);
  });

  it('prints a tied golfer\'s T, a split one\'s share, and a dot between golfers in the golfer line', () => {
    renderWithProviders(
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
                  earnings: 7,
                  ownershipPct: 50,
                }),
                row({
                  round: 2,
                  golferId: 'g-2',
                  golferName: 'SCHAUFFELE',
                  positionStr: '9',
                  finish: { position: 9, tied: false },
                  earnings: 2.5,
                }),
              ],
            }),
          ],
        })}
        onGolferClick={vi.fn()}
      />,
      NO_PROVIDERS,
    );

    const line = golferLine('Aces');
    expect(line).toHaveTextContent(/^MORIKAWA T4 \+\$7\.00 \(50%\) · SCHAUFFELE 9 \+\$2\.50$/);
    expect(line).toHaveClass('text-ink-muted');
    expect(within(line).getByRole('button', { name: 'MORIKAWA' })).toHaveClass(TAP_TARGET_CLASS);
  });

  it('sets the golfer line in the team cell, under the team name', () => {
    renderWithProviders(<ScoreboardView report={report()} onGolferClick={vi.fn()} />, NO_PROVIDERS);

    const name = within(teamTable()).getByText('Aces');
    expect(name).toHaveClass('font-display', 'font-bold', 'text-lg', 'uppercase', 'text-ink');
    expect(name.nextElementSibling).toBe(golferLine('Aces'));
    expect(within(teamCell('Aces')).getByRole('button', { name: 'Scottie Scheffler' })).toBeInTheDocument();
  });

  it('gives a team no golfer line when no golfer of its was paid', () => {
    renderWithProviders(<ScoreboardView report={report({ teams: [team({ cells: [] })] })} />, NO_PROVIDERS);

    const cell = teamCell('Aces');
    expect(cell.children).toHaveLength(1);
    expect(cell).toHaveTextContent(/^Aces$/);
  });

  it('shows the whole leaderboard again when the filter is set back to Team', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger, rory] })} />, NO_PROVIDERS);

    const select = screen.getByLabelText(/Filter leaderboard by team/i);
    await user.selectOptions(select, 'Aces');
    await user.selectOptions(select, 'Team');

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    expect(within(leaderboard).getAllByRole('row').slice(1)).toHaveLength(2);
  });

  it('renders a pair as one row, its names slash-joined and a chip per partner in player order', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [mcIlroyAndLowry] })} />, NO_PROVIDERS);

    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('McIlroy / Lowry')).toBeInTheDocument();
    expect(teamChips(nth(rows, 0)).map((chip) => chip.textContent)).toEqual(['Undrafted', 'PAYRAY']);
  });

  it('outlines the Undrafted chip and fills a team chip', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [mcIlroyAndLowry] })} />, NO_PROVIDERS);

    const [undrafted, payray] = teamChips(nth(within(nth(screen.getAllByRole('table'), 1)).getAllByRole('row'), 1));
    expect(undrafted).toHaveClass('border-hairline-strong', 'text-ink-muted');
    expect(payray).toHaveClass('bg-surface-raised', 'text-ink-body');
  });

  it('gives a golfer split across teams a chip for every team', () => {
    const rahm = makeLeaderboardEntry({ players: [{ name: 'Rahm', teamNames: ['Aces', 'Birdies'] }] });
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [rahm] })} />, NO_PROVIDERS);

    const rahmRow = screen.getByRole('row', { name: /Rahm/ });
    expect(teamChips(rahmRow).map((chip) => chip.textContent)).toEqual(['Aces', 'Birdies']);
  });

  it('filter by a single team name matches pair rows where that team rostered one partner', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [mcIlroyAndLowry, phil] })} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'PAYRAY');
    const leaderboard = nth(screen.getAllByRole('table'), 1);
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('McIlroy / Lowry')).toBeInTheDocument();
  });

  it('sets a rostered leaderboard row in no success wash or success ink', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger, phil] })} />, NO_PROVIDERS);

    const tigerRow = screen.getByRole('row', { name: /Tiger/ });
    expect(tigerRow.className).not.toMatch(/semantic-success/);
    for (const element of tigerRow.querySelectorAll('*')) {
      expect(element.getAttribute('class') ?? '').not.toMatch(/semantic-success/);
    }
  });

  it('sets Score and Tot in semibold and the rounds in muted ink', () => {
    const leader = makeLeaderboardEntry({ scoreToPar: -8, roundScores: [64, 68, 70, 66], totalStrokes: 268 });
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [leader] })} />, NO_PROVIDERS);

    const cells = within(nth(within(nth(screen.getAllByRole('table'), 1)).getAllByRole('row'), 1)).getAllByRole('cell');
    expect(nth(cells, 3)).toHaveClass('font-semibold');
    expect(nth(cells, 8)).toHaveClass('font-semibold');
    for (const round of cells.slice(4, 8)) expect(round).toHaveClass('text-ink-muted');
  });

  it('opens the leaderboard card with a header band naming the tournament', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);

    const band = screen.getByRole('heading', { name: 'Leaderboard' }).parentElement;
    expect(band).toHaveClass('bg-header-band', 'text-on-fill');
    expect(band).toHaveTextContent(/^LeaderboardSample Open$/);
    expect(band?.parentElement).toHaveClass('bg-surface-card');
  });

  it('opens the band with the heading alone when the tournament has no name', () => {
    const unnamed = report({ liveLeaderboard: [tiger] });
    renderWithProviders(
      <ScoreboardView report={{ ...unnamed, tournament: { ...unnamed.tournament, name: null } }} />,
      NO_PROVIDERS,
    );

    const band = screen.getByRole('heading', { name: 'Leaderboard' }).parentElement;
    expect(band?.children).toHaveLength(1);
  });

  it('sets the teams beside the leaderboard card from xl, the teams first', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);

    const card = screen.getByRole('heading', { name: 'Leaderboard' }).closest('.bg-surface-card');
    const columns = card?.parentElement;
    expect(columns).toHaveClass('grid', 'grid-cols-1', 'xl:grid-cols-[minmax(0,5fr)_minmax(0,4fr)]');
    expect(columns?.firstElementChild).toContainElement(nth(screen.getAllByRole('table'), 0));
    expect(columns?.lastElementChild).toBe(card);
  });

  it('sets each team as a white card between its position and money boxes, with no row hover', () => {
    renderWithProviders(<ScoreboardView report={report()} />, NO_PROVIDERS);

    expect(screen.getByRole('table')).toHaveClass('border-separate', 'border-spacing-y-1');
    const row = screen.getByRole('row', { name: /Aces/ });
    expect(row).toHaveClass('h-11');
    expect(row.className).not.toMatch(/hover:/);
    const [, name, topTen] = within(row).getAllByRole('cell');
    for (const cardCell of [name, topTen]) expect(cardCell).toHaveClass('bg-surface-card');
  });

  it('boxes the first position in gold and every other in navy', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          teams: [
            team({ teamId: 't-1', teamName: 'Aces' }),
            team({ teamId: 't-2', teamName: 'Birdies' }),
            team({ teamId: 't-3', teamName: 'Cuts' }),
          ],
        })}
      />,
      NO_PROVIDERS,
    );

    const positions = within(screen.getByRole('table'))
      .getAllByRole('row')
      .slice(1)
      .map((teamRow) => within(teamRow).getAllByRole('cell')[0]);
    expect(positions[0]).toHaveClass('bg-gold');
    expect(positions[1]).toHaveClass('bg-position-fill');
    expect(positions[2]).toHaveClass('bg-position-fill');
  });

  it('fills each Weekly +/- box with the tone its cents print, and leaves Top 10 Earnings in the table ink', () => {
    renderWithProviders(
      <ScoreboardView
        report={report({
          teams: [
            team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 0.4 }),
            team({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: -0.01 }),
            team({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: 0.004 }),
          ],
        })}
      />,
      NO_PROVIDERS,
    );

    expect(within(screen.getByRole('row', { name: /Aces/ })).getByText('+$0.40')).toHaveClass('bg-money-up-fill');
    expect(within(screen.getByRole('row', { name: /Birdies/ })).getByText('−$0.01')).toHaveClass('bg-money-down-fill');
    expect(within(screen.getByRole('row', { name: /Cuts/ })).getByText('$0')).toHaveClass('bg-money-even-fill');
    expect(within(screen.getByRole('row', { name: /Aces/ })).getByText('+$1.00').className).not.toMatch(
      /text-semantic-|text-ink-soft|bg-money-/,
    );
  });

  it('tones the won, lost and net chips by the sign they print, a zero Won gray', () => {
    renderWithProviders(<ScoreboardView report={report({ weeklyTotals: { won: 0, lost: -66, net: -66 } })} />, NO_PROVIDERS);
    expect(screen.getByText('Won').nextSibling).toHaveClass('text-ink-soft');
    expect(screen.getByText('Lost').nextSibling).toHaveClass('text-semantic-danger');
    expect(screen.getByText('Net').nextSibling).toHaveClass('text-semantic-danger');
  });

  it('sets every column head in the shared column-header type', () => {
    renderWithProviders(<ScoreboardView report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);
    expectSharedColumnHeads(2);
  });

  describe('on a phone', () => {
    const threeTeams = [
      team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 0.4 }),
      team({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: -0.01, cells: [] }),
      team({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: 0.004, cells: [] }),
    ];

    it('shows the cards below sm and the team table from sm up', () => {
      renderWithProviders(<ScoreboardView report={report()} />, NO_PROVIDERS);

      expect(screen.getByRole('list', { name: 'Weekly results' })).toHaveClass('sm:hidden');
      expect(teamTable().parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('sets a card per team in the server\'s weekly standings', () => {
      renderWithProviders(
        <ScoreboardView report={report({ teams: threeTeams, weeklyStandings: ['t-3', 't-1', 't-2'] })} />,
        NO_PROVIDERS,
      );

      const placings = phoneCards().map((card) => {
        const [position, middle] = Array.from(card.children);
        return [position?.textContent, middle?.firstElementChild?.textContent];
      });
      expect(placings).toEqual([
        ['1', 'Cuts'],
        ['2', 'Aces'],
        ['3', 'Birdies'],
      ]);
    });

    it('boxes the first position in gold and every other in navy', () => {
      renderWithProviders(<ScoreboardView report={report({ teams: threeTeams })} />, NO_PROVIDERS);

      const positions = phoneCards().map((card) => card.firstElementChild);
      expect(positions[0]).toHaveClass('bg-gold');
      expect(positions[1]).toHaveClass('bg-position-fill');
      expect(positions[2]).toHaveClass('bg-position-fill');
      expect(positions[1]).not.toHaveClass('bg-gold');
    });

    it('prints the team\'s paid golfers under its name, each name a tap-target button', async () => {
      const onGolferClick = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(
        <ScoreboardView
          report={report({
            teams: [
              team({
                teamName: 'Aces',
                cells: [
                  row({ golferId: 'g-1', golferName: 'MORIKAWA', finish: { position: 4, tied: true }, earnings: 7, ownershipPct: 50 }),
                  row({ round: 2, golferId: 'g-2', golferName: 'SCHAUFFELE', finish: { position: 9, tied: false }, earnings: 2.5 }),
                ],
              }),
            ],
          })}
          onGolferClick={onGolferClick}
        />,
        NO_PROVIDERS,
      );

      const line = phoneDetailLine('Aces');
      expect(line).toHaveTextContent(/^MORIKAWA T4 \+\$7\.00 \(50%\) · SCHAUFFELE 9 \+\$2\.50$/);
      const button = within(line).getByRole('button', { name: 'SCHAUFFELE' });
      expect(button).toHaveClass(TAP_TARGET_CLASS);
      await user.click(button);
      expect(onGolferClick).toHaveBeenCalledWith('g-2');
    });

    it('says a team with no paid golfer has none in the top 10, where the table leaves the cell bare', () => {
      renderWithProviders(<ScoreboardView report={report({ teams: [team({ cells: [] })] })} />, NO_PROVIDERS);

      expect(phoneDetailLine('Aces')).toHaveTextContent(/^No golfers in the top 10$/);
      expect(teamCell('Aces')).toHaveTextContent(/^Aces$/);
    });

    it('fills each Weekly +/- box with the tone its cents print', () => {
      renderWithProviders(<ScoreboardView report={report({ teams: threeTeams })} />, NO_PROVIDERS);

      expect(within(phoneCard('Aces')).getByText('+$0.40')).toHaveClass('bg-money-up-fill', 'rounded-r');
      expect(within(phoneCard('Birdies')).getByText('\u2212$0.01')).toHaveClass('bg-money-down-fill');
      expect(within(phoneCard('Cuts')).getByText('$0')).toHaveClass('bg-money-even-fill');
    });
  });
});
