import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import WeeklyReportView from './WeeklyReportView';

function row(round: number, overrides: Partial<ReportCell> = {}): ReportCell {
  return {
    round,
    golferName: `Golfer ${round}`,
    golferId: `g-${round}`,
    positionStr: 'T1',
    finish: { position: 1, tied: true },
    earnings: 0,
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
    liveEligible: false,
    awaitingTeeOff: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
    ...overrides,
  };
}

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const FIRST_TEAM = { selectedTeamId: null, onSelectTeam: () => undefined };

function grid(): HTMLElement {
  return screen.getByRole('table');
}

function phoneSection(): HTMLElement {
  const section = screen.getByRole('group', { name: 'Team' }).parentElement;
  if (!section) throw new Error('the team strip has no section around it');
  return section;
}

function pickCards(): HTMLElement[] {
  return within(screen.getByRole('list', { name: 'Picks' })).getAllByRole('listitem');
}

function teamTotals(): HTMLElement {
  return screen.getByRole('group', { name: 'Team totals' });
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
  const found = within(grid())
    .getAllByRole('row')
    .find((r) => within(r).queryAllByRole('cell')[0]?.textContent.startsWith(`${round}`));
  if (!found) throw new Error(`no row for round ${round}`);
  return found;
}

describe('WeeklyReportView', () => {
  it('renders a column header per team', () => {
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({
          teams: [
            team({ teamId: 't-1', teamName: 'Aces' }),
            team({ teamId: 't-2', teamName: 'Birdies' }),
          ],
        })}
      />,
      NO_PROVIDERS,
    );
    const headers = within(grid()).getAllByRole('columnheader');
    expect(headers[0]).toHaveTextContent('TEAM #');
    expect(headers[1]).toHaveTextContent('Aces');
    expect(headers[2]).toHaveTextContent('Birdies');
  });

  it('sets every team head on the navy band and leaves the corner on the canvas', () => {
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({ teams: [team({ teamId: 't-1' }), team({ teamId: 't-2', teamName: 'Birdies' })] })}
      />,
      NO_PROVIDERS,
    );
    const [corner, ...teamHeads] = within(grid()).getAllByRole('columnheader');
    expect(teamHeads).toHaveLength(2);
    for (const head of teamHeads) expect(head).toHaveClass('bg-header-band', 'text-on-fill');
    expect(corner).not.toHaveClass('bg-header-band');
  });

  it('renders 8 draft-round rows with golfer names from team.cells', () => {
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report()} />, NO_PROVIDERS);
    expect(within(grid()).getByText('Golfer 1')).toBeInTheDocument();
    expect(within(grid()).getByText('Golfer 8')).toBeInTheDocument();
  });

  it('renders the summary rows (TOP TENS / WEEKLY / PREVIOUS / SUBTOTAL / side bets / TOTAL CASH)', () => {
    renderWithProviders(
      <WeeklyReportView {...FIRST_TEAM} report={report({ sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE })} />,
      NO_PROVIDERS,
    );
    expect(within(grid()).getByText('TOP TENS')).toBeInTheDocument();
    expect(within(grid()).getByText('**WEEKLY')).toBeInTheDocument();
    expect(within(grid()).getByText('PREVIOUS')).toBeInTheDocument();
    expect(within(grid()).getByText('SUBTOTAL')).toBeInTheDocument();
    expect(within(grid()).getByText('ROWS 2-3')).toBeInTheDocument();
    // TOTAL CASH cell content is split by <br> so assert via row contents
    const table = grid();
    expect(table).toHaveTextContent('TOTALCASH');
  });

  it("shows the season's own side-bet amount beside its rounds and beside no other", () => {
    renderWithProviders(
      <WeeklyReportView {...FIRST_TEAM} report={report({ sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE })} />,
      NO_PROVIDERS,
    );
    expect(within(grid()).getAllByText('($20.00)')).toHaveLength(2);
    expect(within(roundRow(2)).getByText('($20.00)')).toBeInTheDocument();
    expect(within(roundRow(3)).getByText('($20.00)')).toBeInTheDocument();
    expect(within(roundRow(5)).queryByText(/\(\$/)).not.toBeInTheDocument();
  });

  it("tags exactly the season's late-row rounds", () => {
    renderWithProviders(
      <WeeklyReportView {...FIRST_TEAM} report={report({ sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE })} />,
      NO_PROVIDERS,
    );
    expect(within(grid()).getAllByText('Late row')).toHaveLength(2);
    expect(within(roundRow(2)).getByText('Late row')).toBeInTheDocument();
    expect(within(roundRow(3)).getByText('Late row')).toBeInTheDocument();
  });

  it('tags no round for a season without side bets', () => {
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ sideBetRules: null })} />, NO_PROVIDERS);
    expect(within(grid()).queryByText('Late row')).not.toBeInTheDocument();
  });

  it('shows no amount beside any round and a totals row naming none for a season without side bets', () => {
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ sideBetRules: null, sideBetDetail: [] })} />, NO_PROVIDERS);
    expect(within(grid()).queryByText(/^\(\$/)).not.toBeInTheDocument();
    expect(within(grid()).getByText('SIDE BETS')).toBeInTheDocument();
    expect(within(grid()).queryByText(/^ROWS/)).not.toBeInTheDocument();
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
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={tiedRoundTwo} />, NO_PROVIDERS);

    const [, acesTwo, birdiesTwo] = within(roundRow(2)).getAllByRole('cell');
    expect(acesTwo).toHaveClass('bg-late-row-leader');
    expect(birdiesTwo).toHaveClass('bg-late-row-leader');
    const [, acesThree, birdiesThree] = within(roundRow(3)).getAllByRole('cell');
    expect(acesThree).toHaveClass('bg-late-row-leader');
    expect(birdiesThree).not.toHaveClass('bg-late-row-leader');
    expect(birdiesThree).toHaveClass('bg-surface-card');
    const [, acesFive] = within(roundRow(5)).getAllByRole('cell');
    expect(acesFive).not.toHaveClass('bg-late-row-leader');
  });

  it('leaves an earned cell white and sets its name bold, and mutes the name of a cell that earned nothing', () => {
    const teams = [team({ cells: [row(1, { golferName: 'Earner', earnings: 18 }), row(2, { golferName: 'Blank' })] })];
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ teams })} />, NO_PROVIDERS);

    const earner = within(grid()).getByText('Earner');
    expect(earner.closest('td')).toHaveClass('bg-surface-card');
    expect(earner.closest('td')).not.toHaveClass('bg-late-row-leader');
    expect(earner).toHaveClass('font-bold', 'text-ink');
    const blank = within(grid()).getByText('Blank');
    expect(blank).toHaveClass('text-ink-muted');
    expect(blank).not.toHaveClass('font-bold');
  });

  it('flips the header row label when rendering the season total', () => {
    const seasonReport = report({
      scope: 'season',
      tournament: {
        name: 'All Tournaments',
        startDate: null,
        endDate: null,
        status: null,
        payoutMultiplier: 1,
        week: null,
      },
    });
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={seasonReport} />, NO_PROVIDERS);
    expect(within(grid()).getByText('**SEASON')).toBeInTheDocument();
    expect(within(grid()).queryByText('**WEEKLY')).not.toBeInTheDocument();
  });

  it('shows the season-to-date line under each cell only on a tournament report', () => {
    const teams = [team({ cells: [row(1, { seasonEarnings: 30, seasonTopTens: 2 })] })];

    const { unmount } = renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ teams })} />, NO_PROVIDERS);
    expect(within(grid()).getByText(/\(2\)/)).toBeInTheDocument();
    unmount();

    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ scope: 'season', teams })} />, NO_PROVIDERS);
    expect(within(grid()).queryByText(/\(2\)/)).not.toBeInTheDocument();
  });

  it('renders the undrafted top tens callout when present', () => {
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({
          undraftedTopTens: [
            { name: 'Lefty', payout: 8 },
          ],
        })}
      />,
      NO_PROVIDERS,
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
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={withMissing} />, NO_PROVIDERS);
    const rows = within(grid()).getAllByRole('row');
    // rows[0] = team header, rows[1..8] = rounds 1..8
    const round3 = nth(rows, 3);
    expect(within(round3).getByText('—')).toBeInTheDocument();
  });

  it('shows the TOTAL CASH row in whole dollars and the subtotal in cents', () => {
    renderWithProviders(
      <WeeklyReportView {...FIRST_TEAM} report={report({ teams: [team({ subtotal: 751.5715, totalCash: 1082.4 })] })} />,
      NO_PROVIDERS,
    );
    expect(within(grid()).getByText('+$751.57')).toBeInTheDocument();
    expect(within(grid()).getByText('+$1,082')).toBeInTheDocument();
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
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={disagreeing} />, NO_PROVIDERS);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent(/^\+\$9$/);
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent(/^\u2212\$2$/);
    expect(screen.getByText('Net').nextSibling).toHaveTextContent(/^\+\$7$/);
  });

  it('makes round-cell golfer names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({
          teams: [team({ cells: [row(1, { golferId: 'g-99', golferName: 'Clickable' })] })],
        })}
        onGolferClick={onGolferClick}
      />,
      NO_PROVIDERS,
    );
    await user.click(within(grid()).getByRole('button', { name: /Clickable/ }));
    expect(onGolferClick).toHaveBeenCalledWith('g-99');
  });

  it('gives each round-cell golfer name the tap-target height', () => {
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({
          teams: [team({ cells: [row(1, { golferId: 'g-99', golferName: 'Clickable' })] })],
        })}
        onGolferClick={vi.fn()}
      />,
      NO_PROVIDERS,
    );

    expect(within(grid()).getByRole('button', { name: /Clickable/ })).toHaveClass(TAP_TARGET_CLASS);
  });

  it('renders golfer names as plain text when no onGolferClick is provided', () => {
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({
          teams: [team({ cells: [row(1, { golferId: 'g-1', golferName: 'NotClickable' })] })],
        })}
      />,
      NO_PROVIDERS,
    );
    expect(screen.queryByRole('button', { name: /NotClickable/ })).not.toBeInTheDocument();
    expect(within(grid()).getByText(/NotClickable/)).toBeInTheDocument();
  });

  it('boxes TOTAL CASH by the sign it prints, and leaves the rows above it in the table ink', () => {
    renderWithProviders(
      <WeeklyReportView
        {...FIRST_TEAM}
        report={report({
          sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE,
          teams: [
            team(),
            team({ teamId: 't-2', teamName: 'Birdies', totalCash: -60 }),
            team({ teamId: 't-3', teamName: 'Condors', totalCash: -0.4 }),
          ],
        })}
      />,
      NO_PROVIDERS,
    );
    const summaryRow = (label: string): HTMLElement => {
      const found = screen
        .getAllByRole('row')
        .find((r) => within(r).queryAllByRole('cell')[0]?.textContent === label);
      if (!found) throw new Error(`no summary row labelled ${label}`);
      return found;
    };
    const totalCash = summaryRow('TOTALCASH');
    expect(within(totalCash).getByText('+$170')).toHaveClass('bg-money-up-fill');
    expect(within(totalCash).getByText('\u2212$60')).toHaveClass('bg-money-down-fill');
    expect(within(totalCash).getByText('$0')).toHaveClass('bg-money-even-fill');
    const plainAmounts: readonly [string, string][] = [
      ['TOP TENS', '+$18.00'],
      ['**WEEKLY', '+$25.00'],
      ['SUBTOTAL', '+$125.00'],
      ['ROWS 2-3', '+$45.00'],
    ];
    for (const [label, amount] of plainAmounts) {
      const figure = within(summaryRow(label)).getAllByText(amount)[0];
      expect(figure?.className).not.toMatch(/money-|text-semantic-/);
      expect(figure?.closest('td')).toHaveClass('bg-surface-card');
    }
  });

  it('tones the won, lost and net chips by the sign they print, a zero Won gray', () => {
    renderWithProviders(
      <WeeklyReportView {...FIRST_TEAM} report={report({ cashTotals: { won: 0, lost: -2, net: -2 } })} />,
      NO_PROVIDERS,
    );
    expect(screen.getByText('Won').nextSibling).toHaveClass('text-ink-soft');
    expect(screen.getByText('Lost').nextSibling).toHaveClass('text-semantic-danger');
    expect(screen.getByText('Net').nextSibling).toHaveClass('text-semantic-danger');
  });

  it('sets every column head in the shared column-header type', () => {
    renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report()} />, NO_PROVIDERS);
    expectSharedColumnHeads(1);
  });

  describe('below sm', () => {
    const twoTeams = (): ReportTeamColumn[] => [
      team({ teamId: 't-1', teamName: 'Aces' }),
      team({ teamId: 't-2', teamName: 'Birdies', totalCash: -60 }),
    ];

    function tagsByRound(): (string | null)[] {
      return pickCards().map((card) => within(card).queryByText(/late row/i)?.textContent ?? null);
    }

    it('shows one team as cards below sm, and the grid from sm up', () => {
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report()} />, NO_PROVIDERS);

      expect(phoneSection()).toHaveClass('sm:hidden');
      expect(phoneSection()).not.toHaveClass('-mx-3');
      expect(phoneSection()).toContainElement(screen.getByRole('list', { name: 'Picks' }));
      expect(grid().parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('offers every team on the strip, the first pressed by default', () => {
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ teams: twoTeams() })} />, NO_PROVIDERS);

      const chips = within(screen.getByRole('group', { name: 'Team' })).getAllByRole('button');
      expect(chips.map((chip) => chip.textContent)).toEqual(['Aces', 'Birdies']);
      expect(chips.map((chip) => chip.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
      expect(teamTotals()).toHaveTextContent('Aces');
    });

    it('shows the selected team', () => {
      renderWithProviders(
        <WeeklyReportView report={report({ teams: twoTeams() })} selectedTeamId="t-2" onSelectTeam={vi.fn()} />,
        NO_PROVIDERS,
      );

      expect(teamTotals()).toHaveTextContent('Birdies');
      expect(within(teamTotals()).getByText('\u2212$60')).toHaveClass('bg-money-down-fill');
    });

    it('falls back to the first team when the selected one is not in the report', () => {
      renderWithProviders(
        <WeeklyReportView report={report({ teams: twoTeams() })} selectedTeamId="t-gone" onSelectTeam={vi.fn()} />,
        NO_PROVIDERS,
      );

      expect(teamTotals()).toHaveTextContent('Aces');
      expect(within(screen.getByRole('group', { name: 'Team' })).getByRole('button', { name: 'Aces' })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
    });

    it('reports the tapped team', async () => {
      const onSelectTeam = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(
        <WeeklyReportView report={report({ teams: twoTeams() })} selectedTeamId={null} onSelectTeam={onSelectTeam} />,
        NO_PROVIDERS,
      );

      await user.click(within(screen.getByRole('group', { name: 'Team' })).getByRole('button', { name: 'Birdies' }));

      expect(onSelectTeam).toHaveBeenCalledWith('t-2');
    });

    it('heads the cards with the team total, then previous, week and late row, then its top 10s', () => {
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report()} />, NO_PROVIDERS);

      expect(within(teamTotals()).getByText('Total cash')).toBeInTheDocument();
      expect(within(teamTotals()).getByText('+$170')).toHaveClass('bg-money-up-fill');
      const figures = within(teamTotals())
        .getAllByRole('term')
        .map((term) => [term.textContent, term.nextElementSibling?.textContent]);
      expect(figures).toEqual([
        ['Previous', '+$100.00'],
        ['Week', '+$25.00'],
        ['Late row', '+$45.00'],
      ]);
      expect(teamTotals()).toHaveTextContent('1 top 10 this season · +$18.00');
    });

    it('names the middle figure for the season on a season report', () => {
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ scope: 'season' })} />, NO_PROVIDERS);

      expect(within(teamTotals()).getByText('Season')).toBeInTheDocument();
      expect(within(teamTotals()).queryByText('Week')).not.toBeInTheDocument();
    });

    it('counts the top 10s in the plural past one', () => {
      renderWithProviders(
        <WeeklyReportView {...FIRST_TEAM} report={report({ teams: [team({ topTenCount: 3, topTenMoney: 40 })] })} />,
        NO_PROVIDERS,
      );

      expect(teamTotals()).toHaveTextContent('3 top 10s this season · +$40.00');
    });

    it('sets a card per round, numbered in navy with round 1 never gold', () => {
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report()} />, NO_PROVIDERS);

      const boxes = pickCards().map((card) => nth(Array.from(card.children), 0));
      expect(boxes.map((box) => box.textContent)).toEqual(['1', '2', '3', '4', '5', '6', '7', '8']);
      for (const box of boxes) {
        expect(box).toHaveClass('bg-position-fill');
        expect(box).not.toHaveClass('bg-gold');
      }
    });

    it("prints a pick's name and share, its finish and money, and its season to date", () => {
      const cells = [
        row(1, { golferName: 'Earner', ownershipPct: 60, positionStr: 'T3', earnings: 9.5, seasonEarnings: 30, seasonTopTens: 2 }),
        row(2, { golferName: 'Once', seasonEarnings: 18, seasonTopTens: 1 }),
      ];
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ teams: [team({ cells })] })} />, NO_PROVIDERS);

      const [earner, once] = pickCards();
      expect(earner).toHaveTextContent('Earner (60%)');
      expect(earner).toHaveTextContent('T3 - +$9.50');
      expect(earner).toHaveTextContent('+$30.00 · 2 top 10s');
      expect(once).toHaveTextContent(/-\+\$18\.00 · 1 top 10$/);
      expect(within(once ?? document.body).getByText('Once')).toHaveClass('text-ink-muted');
    });

    it('leaves the season-to-date line off a season report', () => {
      const cells = [row(1, { seasonEarnings: 30, seasonTopTens: 2 })];
      renderWithProviders(
        <WeeklyReportView {...FIRST_TEAM} report={report({ scope: 'season', teams: [team({ cells })] })} />,
        NO_PROVIDERS,
      );

      expect(nth(pickCards(), 0)).not.toHaveTextContent('top 10');
    });

    it('sets a dash for an empty draft slot', () => {
      const cells = [1, 2, 3, 4, 5, 6, 7, 8].map((r) => (r === 3 ? row(r, { golferName: null }) : row(r)));
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ teams: [team({ cells })] })} />, NO_PROVIDERS);

      expect(nth(pickCards(), 2)).toHaveTextContent(/^3—$/);
    });

    it("tags exactly the season's late-row rounds, outlined where the team doesn't lead", () => {
      renderWithProviders(
        <WeeklyReportView {...FIRST_TEAM} report={report({ sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE })} />,
        NO_PROVIDERS,
      );

      expect(tagsByRound()).toEqual([null, 'Late row', 'Late row', null, null, null, null, null]);
      for (const card of pickCards()) expect(card).toHaveClass('bg-surface-card');
    });

    it('fills a round the team leads and gives it the gold tag, and no round it lost', () => {
      const leadsTwo = report({
        sideBetRules: TWENTY_ON_ROUNDS_TWO_AND_THREE,
        sideBetDetail: [
          { round: 2, teams: [sideBetEntry('t-1', 20, 'won')] },
          { round: 3, teams: [sideBetEntry('t-1', -20, 'lost')] },
        ],
      });
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={leadsTwo} />, NO_PROVIDERS);

      expect(tagsByRound()).toEqual([null, 'Leads late row', 'Late row', null, null, null, null, null]);
      const fills = pickCards().map((card) => card.classList.contains('bg-late-row-leader'));
      expect(fills).toEqual([false, true, false, false, false, false, false, false]);
      expect(within(nth(pickCards(), 1)).getByText('Leads late row')).toHaveClass('bg-gold');
    });

    it('opens a golfer from their card', async () => {
      const onGolferClick = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(
        <WeeklyReportView
          {...FIRST_TEAM}
          report={report({ teams: [team({ cells: [row(1, { golferId: 'g-99', golferName: 'Clickable' })] })] })}
          onGolferClick={onGolferClick}
        />,
        NO_PROVIDERS,
      );

      const golfer = within(nth(pickCards(), 0)).getByRole('button', { name: 'Clickable' });
      await user.click(golfer);

      expect(onGolferClick).toHaveBeenCalledWith('g-99');
      expect(golfer).toHaveClass(TAP_TARGET_CLASS);
    });

    it('shows no strip or cards for a report with no teams', () => {
      renderWithProviders(<WeeklyReportView {...FIRST_TEAM} report={report({ teams: [] })} />, NO_PROVIDERS);

      expect(screen.queryByRole('group', { name: 'Team' })).not.toBeInTheDocument();
      expect(screen.queryByRole('list', { name: 'Picks' })).not.toBeInTheDocument();
    });

    it('sets the undrafted top tens and the cash chips once, under both layouts', () => {
      renderWithProviders(
        <WeeklyReportView {...FIRST_TEAM} report={report({ undraftedTopTens: [{ name: 'Lefty', payout: 8 }] })} />,
        NO_PROVIDERS,
      );

      expect(screen.getAllByText(/UNDRAFTED TOP TENS/)).toHaveLength(1);
      expect(screen.getAllByText('Won')).toHaveLength(1);
    });
  });
});
