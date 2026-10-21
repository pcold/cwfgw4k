import { useState, type ReactNode } from 'react';
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
    inField: false,
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

function leaderboardTable(): HTMLElement {
  return nth(screen.getAllByRole('table'), 1);
}

function leaderboardTableNames(): (string | null | undefined)[] {
  return within(leaderboardTable())
    .getAllByRole('row')
    .slice(1)
    .map((tableRow) => within(tableRow).getAllByRole('cell')[1]?.textContent);
}

function phoneLeaderboard(): HTMLElement {
  return screen.getByRole('list', { name: 'Leaderboard' });
}

function phoneLeaderboardRows(): HTMLElement[] {
  return within(phoneLeaderboard()).getAllByRole('listitem');
}

interface PhoneLeaderboardRow {
  position: Element | undefined;
  name: Element | null | undefined;
  chips: Element[];
  roundLine: Element | undefined;
  score: Element | undefined;
}

function phoneLeaderboardRow(item: HTMLElement): PhoneLeaderboardRow {
  const [position, middle, score] = Array.from(item.children);
  const [chips, roundLine] = Array.from(middle?.children[1]?.children ?? []);
  return {
    position,
    name: middle?.firstElementChild,
    chips: chips ? Array.from(chips.children) : [],
    roundLine,
    score,
  };
}

function phoneTeamFilter(): HTMLElement {
  return screen.getByRole('combobox', { name: 'Team' });
}

interface RefetchingProps {
  first: WeeklyReport;
  second: WeeklyReport;
}

/** The leaderboard's filter outlives a refetch, so a team it names can drop out of the rows. */
function Refetching({ first, second }: RefetchingProps): ReactNode {
  const [current, setCurrent] = useState(first);
  return (
    <>
      <button type="button" onClick={() => setCurrent(second)}>
        Refetch
      </button>
      <ScoreboardView myTeamName={null} report={current} />
    </>
  );
}

describe('ScoreboardView', () => {
  it('shows the "Results finalized" banner for completed tournaments', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report()} />, NO_PROVIDERS);
    expect(screen.getByText(/Results finalized/i)).toBeInTheDocument();
  });

  it('shows the in-progress banner for non-completed tournaments', () => {
    renderWithProviders(
      <ScoreboardView
        myTeamName={null}
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
        myTeamName={null}
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
        myTeamName={null}
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
        myTeamName={null}
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
        myTeamName={null}
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
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, phil] })} />, NO_PROVIDERS);
    expect(screen.getByRole('heading', { name: /Leaderboard/i })).toBeInTheDocument();
    const leaderboard = leaderboardTable();
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell').slice(0, 4).map((c) => c.textContent))).toEqual([
      ['3', 'Tiger', 'Aces', '-8'],
      ['5', 'Phil', 'Undrafted', 'E'],
    ]);
  });

  it('keeps the server\'s leaderboard order rather than sorting by position', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [phil, tiger] })} />, NO_PROVIDERS);
    const leaderboard = leaderboardTable();
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell')[1]?.textContent)).toEqual(['Phil', 'Tiger']);
  });

  it('prints a dash for the position and score of a slot yet to tee off', () => {
    const lateWave = makeLeaderboardEntry({ position: null, scoreToPar: null });
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [lateWave] })} />, NO_PROVIDERS);
    const leaderboard = leaderboardTable();
    const cells = within(nth(within(leaderboard).getAllByRole('row'), 1)).getAllByRole('cell');
    expect([nth(cells, 0), nth(cells, 3)].map((c) => c.textContent)).toEqual(['—', '—']);
  });

  it('prints round scores and the stroke total, and a dash for a round not yet played', () => {
    const leader = makeLeaderboardEntry({ scoreToPar: null, roundScores: [64, 68], totalStrokes: 132 });
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [leader] })} />, NO_PROVIDERS);
    const leaderboard = leaderboardTable();
    const cells = within(nth(within(leaderboard).getAllByRole('row'), 1)).getAllByRole('cell');
    expect(cells.slice(3).map((c) => c.textContent)).toEqual(['—', '64', '68', '—', '—', '132']);
  });

  it('shows the won, lost and net the server totalled, not a sum of the team rows', () => {
    renderWithProviders(
      <ScoreboardView
        myTeamName={null}
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
        myTeamName={null}
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
        myTeamName={null}
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
        myTeamName={null}
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
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [] })} />, NO_PROVIDERS);
    expect(screen.queryByRole('heading', { name: /Leaderboard/i })).not.toBeInTheDocument();
    expect(screen.getByRole('table').closest('.grid')).toBeNull();
  });

  it('filters the leaderboard to rostered golfers on the selected team', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory, phil] })} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Aces');

    const leaderboard = leaderboardTable();
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Tiger')).toBeInTheDocument();
  });

  it('offers Undrafted on the leaderboard filter when undrafted golfers are present', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, phil] })} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'Undrafted');

    const leaderboard = leaderboardTable();
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Phil')).toBeInTheDocument();
  });

  it('offers no Undrafted option when every leaderboard player is rostered', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory] })} />, NO_PROVIDERS);
    const options = within(screen.getByLabelText(/Filter leaderboard by team/i)).getAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual(['Team', 'Aces', 'Birdies']);
  });

  it('omits the T prefix on a solo position in the golfer line', () => {
    renderWithProviders(
      <ScoreboardView
        myTeamName={null}
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
        myTeamName={null}
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
    renderWithProviders(<ScoreboardView myTeamName={null} report={report()} onGolferClick={vi.fn()} />, NO_PROVIDERS);

    const name = within(teamTable()).getByText('Aces');
    expect(name).toHaveClass('font-display', 'font-bold', 'text-lg', 'uppercase', 'text-ink');
    expect(name.nextElementSibling).toBe(golferLine('Aces'));
    expect(within(teamCell('Aces')).getByRole('button', { name: 'Scottie Scheffler' })).toBeInTheDocument();
  });

  it('gives a team no golfer line when no golfer of its was paid', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ teams: [team({ cells: [] })] })} />, NO_PROVIDERS);

    const cell = teamCell('Aces');
    expect(cell.children).toHaveLength(1);
    expect(cell).toHaveTextContent(/^Aces$/);
  });

  it('shows the whole leaderboard again when the filter is set back to Team', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory] })} />, NO_PROVIDERS);

    const select = screen.getByLabelText(/Filter leaderboard by team/i);
    await user.selectOptions(select, 'Aces');
    await user.selectOptions(select, 'Team');

    const leaderboard = leaderboardTable();
    expect(within(leaderboard).getAllByRole('row').slice(1)).toHaveLength(2);
  });

  it('renders a pair as one row, its names slash-joined and a chip per partner in player order', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [mcIlroyAndLowry] })} />, NO_PROVIDERS);

    const leaderboard = leaderboardTable();
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('McIlroy / Lowry')).toBeInTheDocument();
    expect(teamChips(nth(rows, 0)).map((chip) => chip.textContent)).toEqual(['Undrafted', 'PAYRAY']);
  });

  it('outlines the Undrafted chip and fills a team chip', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [mcIlroyAndLowry] })} />, NO_PROVIDERS);

    const [undrafted, payray] = teamChips(nth(within(leaderboardTable()).getAllByRole('row'), 1));
    expect(undrafted).toHaveClass('border-hairline-strong', 'text-ink-muted');
    expect(payray).toHaveClass('bg-surface-raised', 'text-ink-body');
  });

  it('gives a golfer split across teams a chip for every team', () => {
    const rahm = makeLeaderboardEntry({ players: [{ name: 'Rahm', teamNames: ['Aces', 'Birdies'] }] });
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [rahm] })} />, NO_PROVIDERS);

    const rahmRow = screen.getByRole('row', { name: /Rahm/ });
    expect(teamChips(rahmRow).map((chip) => chip.textContent)).toEqual(['Aces', 'Birdies']);
  });

  it('filter by a single team name matches pair rows where that team rostered one partner', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [mcIlroyAndLowry, phil] })} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByLabelText(/Filter leaderboard by team/i), 'PAYRAY');
    const leaderboard = leaderboardTable();
    const rows = within(leaderboard).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('McIlroy / Lowry')).toBeInTheDocument();
  });

  it('sets a rostered leaderboard row in no success wash or success ink', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, phil] })} />, NO_PROVIDERS);

    const tigerRow = screen.getByRole('row', { name: /Tiger/ });
    expect(tigerRow.className).not.toMatch(/semantic-success/);
    for (const element of tigerRow.querySelectorAll('*')) {
      expect(element.getAttribute('class') ?? '').not.toMatch(/semantic-success/);
    }
  });

  it('sets Score and Tot in semibold and the rounds in muted ink', () => {
    const leader = makeLeaderboardEntry({ scoreToPar: -8, roundScores: [64, 68, 70, 66], totalStrokes: 268 });
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [leader] })} />, NO_PROVIDERS);

    const cells = within(nth(within(leaderboardTable()).getAllByRole('row'), 1)).getAllByRole('cell');
    expect(nth(cells, 3)).toHaveClass('font-semibold');
    expect(nth(cells, 8)).toHaveClass('font-semibold');
    for (const round of cells.slice(4, 8)) expect(round).toHaveClass('text-ink-muted');
  });

  it('opens the leaderboard card with a header band naming the tournament', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);

    const band = screen.getByRole('heading', { name: 'Leaderboard' }).parentElement;
    expect(band).toHaveClass('bg-header-band', 'text-on-fill');
    expect(band).toHaveTextContent(/^LeaderboardSample Open$/);
    expect(band?.parentElement).toHaveClass('bg-surface-card');
  });

  it('opens the band with the heading alone when the tournament has no name', () => {
    const unnamed = report({ liveLeaderboard: [tiger] });
    renderWithProviders(
      <ScoreboardView myTeamName={null} report={{ ...unnamed, tournament: { ...unnamed.tournament, name: null } }} />,
      NO_PROVIDERS,
    );

    const band = screen.getByRole('heading', { name: 'Leaderboard' }).parentElement;
    expect(band?.children).toHaveLength(1);
  });

  it('sets the teams beside the leaderboard card from xl, the teams first', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);

    const card = screen.getByRole('heading', { name: 'Leaderboard' }).closest('.bg-surface-card');
    const columns = card?.parentElement;
    expect(columns).toHaveClass('grid', 'grid-cols-1', 'xl:grid-cols-[minmax(0,5fr)_minmax(0,4fr)]');
    expect(columns?.firstElementChild).toContainElement(nth(screen.getAllByRole('table'), 0));
    expect(columns?.lastElementChild).toBe(card);
  });

  it('sets each team as a white card between its position and money boxes, with no row hover', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report()} />, NO_PROVIDERS);

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
        myTeamName={null}
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
        myTeamName={null}
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
    renderWithProviders(
      <ScoreboardView myTeamName={null} report={report({ weeklyTotals: { won: 0, lost: -66, net: -66 } })} />,
      NO_PROVIDERS,
    );
    expect(screen.getByText('Won').nextSibling).toHaveClass('text-ink-soft');
    expect(screen.getByText('Lost').nextSibling).toHaveClass('text-semantic-danger');
    expect(screen.getByText('Net').nextSibling).toHaveClass('text-semantic-danger');
  });

  it('sets every column head in the shared column-header type', () => {
    renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);
    expectSharedColumnHeads(2);
  });

  describe("the visitor's own team", () => {
    const threeTeams = [
      team({ teamId: 't-1', teamName: 'Aces', cells: [] }),
      team({ teamId: 't-2', teamName: 'Birdies', cells: [] }),
      team({ teamId: 't-3', teamName: 'Cuts', cells: [] }),
    ];
    const inProgress = { ...report().tournament, status: 'in_progress' };
    const phases = [
      ['live', report({ teams: threeTeams, tournament: inProgress })],
      ['before tee-off', report({ teams: threeTeams, tournament: inProgress, awaitingTeeOff: true })],
    ] as const;

    function teamRow(teamName: string): HTMLElement {
      const tableRow = teamCell(teamName).closest('tr');
      if (!tableRow) throw new Error(`no table row for ${teamName}`);
      return tableRow;
    }

    it.each(phases)('tags its card and its table row and no other, %s', (_phase, scoreboard) => {
      renderWithProviders(<ScoreboardView myTeamName="birdies" report={scoreboard} />, NO_PROVIDERS);

      expect(within(phoneCard('Birdies')).getByText('your team')).toBeInTheDocument();
      expect(within(teamRow('Birdies')).getByText('your team')).toBeInTheDocument();
      expect(screen.getAllByText('your team')).toHaveLength(2);
    });

    // Styling alone, so the classes are what there is to assert.
    it.each(phases)('rings its row in gold and fills its card and team cell pale gold, %s', (_phase, scoreboard) => {
      renderWithProviders(<ScoreboardView myTeamName="Birdies" report={scoreboard} />, NO_PROVIDERS);

      expect(teamRow('Birdies')).toHaveClass('outline-2', 'outline-gold');
      expect(teamCell('Birdies')).toHaveClass('bg-my-team');
      expect(phoneCard('Birdies')).toHaveClass('bg-my-team', 'ring-gold');
      expect(teamRow('Aces')).not.toHaveClass('outline-2');
      expect(teamCell('Aces')).toHaveClass('bg-surface-card');
    });

    it.each(phases)('marks nothing when no team is chosen, %s', (_phase, scoreboard) => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={scoreboard} />, NO_PROVIDERS);

      expect(screen.queryByText('your team')).toBeNull();
      expect(phoneCard('Birdies')).not.toHaveClass('bg-my-team');
    });
  });

  describe('on a phone', () => {
    const threeTeams = [
      team({ teamId: 't-1', teamName: 'Aces', weeklyTotal: 0.4 }),
      team({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: -0.01, cells: [] }),
      team({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: 0.004, cells: [] }),
    ];

    it('shows the cards below sm and the team table from sm up', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={report()} />, NO_PROVIDERS);

      expect(screen.getByRole('list', { name: 'Weekly results' })).toHaveClass('sm:hidden');
      expect(teamTable().parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('sets a card per team in the server\'s weekly standings', () => {
      renderWithProviders(
        <ScoreboardView myTeamName={null} report={report({ teams: threeTeams, weeklyStandings: ['t-3', 't-1', 't-2'] })} />,
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
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ teams: threeTeams })} />, NO_PROVIDERS);

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
          myTeamName={null}
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
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ teams: [team({ cells: [] })] })} />, NO_PROVIDERS);

      expect(phoneDetailLine('Aces')).toHaveTextContent(/^No golfers in the top 10$/);
      expect(teamCell('Aces')).toHaveTextContent(/^Aces$/);
    });

    it('fills each Weekly +/- box with the tone its cents print', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ teams: threeTeams })} />, NO_PROVIDERS);

      expect(within(phoneCard('Aces')).getByText('+$0.40')).toHaveClass('bg-money-up-fill', 'rounded-r');
      expect(within(phoneCard('Birdies')).getByText('\u2212$0.01')).toHaveClass('bg-money-down-fill');
      expect(within(phoneCard('Cuts')).getByText('$0')).toHaveClass('bg-money-even-fill');
    });
  });
  describe('the leaderboard on a phone', () => {
    const unplacedLate = makeLeaderboardEntry({
      position: null,
      scoreToPar: null,
      players: [{ name: 'Late Wave', teamNames: [] }],
    });
    const unplacedLater = makeLeaderboardEntry({
      position: null,
      scoreToPar: null,
      players: [{ name: 'Later Wave', teamNames: ['Aces'] }],
    });

    it('shows the rows and their team filter below sm and the table from sm up', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);

      const phoneOnly = phoneLeaderboard().parentElement;
      expect(phoneOnly).toHaveClass('sm:hidden');
      expect(phoneOnly).toContainElement(phoneTeamFilter());
      expect(leaderboardTable().parentElement).toHaveClass('hidden', 'sm:block', 'overflow-x-auto');
      expect(leaderboardTable()).toHaveClass('min-w-[480px]');
    });

    it('sets a row per entry in the server\'s order, the unplaced last, each its position, name and score', () => {
      renderWithProviders(
        <ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory, unplacedLate, unplacedLater] })} />,
        NO_PROVIDERS,
      );

      const rows = phoneLeaderboardRows().map(phoneLeaderboardRow);
      expect(rows.map((r) => [r.position?.textContent, r.name?.textContent, r.score?.textContent])).toEqual([
        ['3', 'Tiger', '-8'],
        ['5', 'Rory', '-6'],
        ['—', 'Late Wave', '—'],
        ['—', 'Later Wave', '—'],
      ]);
    });

    it('sets the position muted in a fixed column, the name truncating, and the score semibold', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger] })} />, NO_PROVIDERS);

      const { position, name, score } = phoneLeaderboardRow(nth(phoneLeaderboardRows(), 0));
      expect(position).toHaveClass('w-8', 'shrink-0', 'text-ink-muted', 'tabular-nums');
      expect(name).toHaveClass('truncate', 'text-ink');
      expect(name?.parentElement).toHaveClass('min-w-0', 'flex-1');
      expect(score).toHaveClass('font-semibold', 'tabular-nums');
      expect(phoneLeaderboard()).toHaveClass('divide-y', 'divide-hairline-soft');
    });

    it('gives a pair a chip per partner, the undrafted one outlined, and a split golfer a chip per team', () => {
      const rahm = makeLeaderboardEntry({ players: [{ name: 'Rahm', teamNames: ['Aces', 'Birdies'] }] });
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [mcIlroyAndLowry, rahm] })} />, NO_PROVIDERS);

      const [pair, split] = phoneLeaderboardRows().map(phoneLeaderboardRow);
      expect(pair?.chips.map((chip) => chip.textContent)).toEqual(['Undrafted', 'PAYRAY']);
      expect(pair?.chips[0]).toHaveClass('border-hairline-strong', 'text-ink-muted');
      expect(split?.chips.map((chip) => chip.textContent)).toEqual(['Aces', 'Birdies']);
    });

    it('prints the rounds played and the total beside the chips, in muted figures', () => {
      const leader = makeLeaderboardEntry({ roundScores: [68, 70], totalStrokes: 138 });
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [leader] })} />, NO_PROVIDERS);

      const { roundLine } = phoneLeaderboardRow(nth(phoneLeaderboardRows(), 0));
      expect(roundLine).toHaveTextContent(/^R1 68 · R2 70 · Tot 138$/);
      expect(roundLine).toHaveClass('text-xs', 'text-ink-muted', 'tabular-nums');
    });

    it('shows a competitor yet to tee off with chips alone', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [unplacedLate] })} />, NO_PROVIDERS);

      const { chips, roundLine } = phoneLeaderboardRow(nth(phoneLeaderboardRows(), 0));
      expect(chips.map((chip) => chip.textContent)).toEqual(['Undrafted']);
      expect(roundLine).toBeUndefined();
    });

    it('offers every team and Undrafted after All teams', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory, phil] })} />, NO_PROVIDERS);

      const options = within(phoneTeamFilter()).getAllByRole('option');
      expect(options.map((option) => option.textContent)).toEqual(['All teams', 'Aces', 'Birdies', 'Undrafted']);
    });

    it('filters both the rows and the table by the phone select, and the header filter follows it', async () => {
      const user = userEvent.setup();
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory, phil] })} />, NO_PROVIDERS);

      await user.selectOptions(phoneTeamFilter(), 'Aces');

      expect(phoneLeaderboardRows().map((item) => phoneLeaderboardRow(item).name?.textContent)).toEqual(['Tiger']);
      expect(leaderboardTableNames()).toEqual(['Tiger']);
      expect(screen.getByLabelText(/Filter leaderboard by team/i)).toHaveValue('Aces');
    });

    it('filters both lists to the rows with an undrafted player when Undrafted is picked', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, mcIlroyAndLowry, rory, phil] })} />,
        NO_PROVIDERS,
      );

      await user.selectOptions(phoneTeamFilter(), 'Undrafted');

      const phoneNames = phoneLeaderboardRows().map((item) => phoneLeaderboardRow(item).name?.textContent);
      expect(phoneNames).toEqual(['McIlroy / Lowry', 'Phil']);
      expect(leaderboardTableNames()).toEqual(['McIlroy / Lowry', 'Phil']);
    });

    it('shows every row again when All teams is picked back', async () => {
      const user = userEvent.setup();
      renderWithProviders(<ScoreboardView myTeamName={null} report={report({ liveLeaderboard: [tiger, rory] })} />, NO_PROVIDERS);

      await user.selectOptions(phoneTeamFilter(), 'Aces');
      await user.selectOptions(phoneTeamFilter(), 'All teams');

      expect(phoneLeaderboardRows()).toHaveLength(2);
    });

    it('says no player matches in place of the rows when the filter leaves none', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <Refetching
          first={report({ liveLeaderboard: [tiger, rory] })}
          second={report({ liveLeaderboard: [rory] })}
        />,
        NO_PROVIDERS,
      );

      await user.selectOptions(phoneTeamFilter(), 'Aces');
      await user.click(screen.getByRole('button', { name: 'Refetch' }));

      expect(screen.queryByRole('list', { name: 'Leaderboard' })).not.toBeInTheDocument();
      const phoneMessage = screen
        .getAllByText('No players match this filter.')
        .filter((message) => !leaderboardTable().contains(message));
      expect(phoneMessage).toHaveLength(1);
      expect(nth(phoneMessage, 0).parentElement).toHaveClass('sm:hidden');
    });
  });

  describe('before tee-off', () => {
    function playing(round: number, golferId: string, golferName: string): ReportCell {
      return row({ round, golferId, golferName, inField: true, positionStr: null, finish: null, earnings: 0 });
    }

    const fieldTeams = [
      team({
        teamId: 't-1',
        teamName: 'Aces',
        weeklyTotal: 0,
        cells: [
          playing(1, 'g-1', 'Scottie Scheffler'),
          row({ round: 2, golferId: 'g-2', golferName: 'Rory McIlroy', earnings: 0, finish: null }),
          playing(3, 'g-3', 'Xander Schauffele'),
        ],
      }),
      team({ teamId: 't-2', teamName: 'Birdies', weeklyTotal: 0, cells: [playing(1, 'g-4', 'Ludvig Aberg')] }),
      team({ teamId: 't-3', teamName: 'Cuts', weeklyTotal: 0, cells: [] }),
    ];

    function preStart(teams: ReportTeamColumn[]): WeeklyReport {
      return report({ teams, tournament: { ...report().tournament, status: 'in_progress' }, awaitingTeeOff: true });
    }

    it('lists each team\'s golfers in the field in draft order after a count, with no finish or money', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={preStart(fieldTeams)} />, NO_PROVIDERS);

      expect(golferLine('Aces')).toHaveTextContent(/^2 playing · Scottie Scheffler · Xander Schauffele$/);
      expect(phoneDetailLine('Aces')).toHaveTextContent(/^2 playing · Scottie Scheffler · Xander Schauffele$/);
      expect(golferLine('Birdies')).toHaveTextContent(/^1 playing · Ludvig Aberg$/);
      expect(phoneDetailLine('Birdies')).toHaveTextContent(/^1 playing · Ludvig Aberg$/);
    });

    it('says a team with nobody in the field has 0 playing and nothing else', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={preStart(fieldTeams)} />, NO_PROVIDERS);

      expect(golferLine('Cuts')).toHaveTextContent(/^0 playing$/);
      expect(phoneDetailLine('Cuts')).toHaveTextContent(/^0 playing$/);
    });

    it('shows the cards below sm and the table from sm up, as the paid list does', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={preStart(fieldTeams)} />, NO_PROVIDERS);

      expect(screen.getByRole('list', { name: 'Weekly results' })).toHaveClass('sm:hidden');
      expect(teamTable().parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('sets no money box on a card and no money column in the table', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={preStart(fieldTeams)} />, NO_PROVIDERS);

      expect(within(teamTable()).getAllByRole('columnheader').map((head) => head.textContent)).toEqual(['#', 'Team']);
      expect(teamTable()).not.toHaveTextContent('$');
      expect(phoneCard('Aces').children).toHaveLength(2);
      expect(screen.getByRole('list', { name: 'Weekly results' })).not.toHaveTextContent('$');
    });

    it('makes each name in the field a tap-target button that opens the golfer, in the table and on the phone', async () => {
      const onGolferClick = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(<ScoreboardView myTeamName={null} report={preStart(fieldTeams)} onGolferClick={onGolferClick} />, NO_PROVIDERS);

      const tableButton = within(golferLine('Aces')).getByRole('button', { name: 'Xander Schauffele' });
      expect(tableButton).toHaveClass(TAP_TARGET_CLASS);
      await user.click(tableButton);
      expect(onGolferClick).toHaveBeenLastCalledWith('g-3');
      await user.click(within(phoneDetailLine('Birdies')).getByRole('button', { name: 'Ludvig Aberg' }));
      expect(onGolferClick).toHaveBeenLastCalledWith('g-4');
    });

    it('prints the names as plain text when no onGolferClick is provided', () => {
      renderWithProviders(<ScoreboardView myTeamName={null} report={preStart(fieldTeams)} />, NO_PROVIDERS);

      expect(within(golferLine('Aces')).queryByRole('button')).not.toBeInTheDocument();
      expect(within(golferLine('Aces')).getByText('Scottie Scheffler')).toHaveClass('text-ink-secondary');
    });

    it.each([
      ['live', { status: 'in_progress', awaitingTeeOff: false }],
      ['finalized', { status: 'completed', awaitingTeeOff: false }],
    ])('lists only the paid, with their money, once %s', (_phase, { status, awaitingTeeOff }) => {
      const cells = [
        row({ golferId: 'g-1', golferName: 'Scottie Scheffler', inField: true, earnings: 18, finish: { position: 1, tied: false } }),
        row({ round: 2, golferId: 'g-2', golferName: 'Rory McIlroy', inField: true, earnings: 0, finish: null }),
      ];
      renderWithProviders(
        <ScoreboardView
          myTeamName={null}
          report={report({
            teams: [team({ teamName: 'Aces', cells })],
            tournament: { ...report().tournament, status },
            awaitingTeeOff,
          })}
        />,
        NO_PROVIDERS,
      );

      expect(golferLine('Aces')).toHaveTextContent(/^Scottie Scheffler 1 \+\$18\.00$/);
      expect(phoneDetailLine('Aces')).toHaveTextContent(/^Scottie Scheffler 1 \+\$18\.00$/);
      expect(screen.queryByText(/playing/)).not.toBeInTheDocument();
    });
  });
});
