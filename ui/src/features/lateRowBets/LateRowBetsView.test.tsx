import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type {
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  SideBetOutcome,
  WeeklyReport,
} from '@/shared/api/types';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import { nth } from '@/shared/test/nth';
import LateRowBetsView from './LateRowBetsView';

function team(overrides: Partial<ReportTeamColumn> = {}): ReportTeamColumn {
  return {
    teamId: 't-1',
    teamName: 'Aces',
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
    liveEligible: false,
    awaitingTeeOff: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
    sideBetStandings: ['t-1', 't-3', 't-2'],
    ...overrides,
  };
}

const FIRST_TAB = { selectedTab: null, onSelectTab: () => undefined };

function phoneSection(): HTMLElement {
  const section = screen.getByRole('group', { name: 'Late row round' }).parentElement;
  if (!section) throw new Error('the round tabs have no section around them');
  return section;
}

function desktop(): HTMLElement {
  const wrapper = phoneSection().nextElementSibling;
  if (!(wrapper instanceof HTMLElement)) throw new Error('no desktop layout follows the phone section');
  return wrapper;
}

function roundTable(round: number): HTMLElement {
  const section = within(desktop()).getByRole('heading', { name: `Round ${round}` }).parentElement;
  if (!section) throw new Error(`the Round ${round} heading has no section around it`);
  return section;
}

function totalsTable(): HTMLElement {
  const section = within(desktop()).getByRole('heading', { name: /Total Late Row Bets/i }).parentElement;
  if (!section) throw new Error('the totals heading has no section around it');
  return section;
}

function chips(): HTMLElement[] {
  return within(screen.getByRole('group', { name: 'Late row round' })).getAllByRole('button');
}

function leaderCards(): HTMLElement[] {
  return within(within(phoneSection()).getByRole('list', { name: 'Leaders' })).getAllByRole('listitem');
}

function phoneList(heading: string | RegExp): HTMLElement {
  return within(phoneSection()).getByRole('list', { name: heading });
}

describe('LateRowBetsView', () => {
  it("states the season's own amount and rounds in the rules sentence, once for both layouts", () => {
    render(<LateRowBetsView report={report()} {...FIRST_TAB} />);
    expect(
      screen.getAllByText(
        "$20.00 per team in each of draft rounds 2 and 3. Every team behind a round's highest " +
          'cumulative earner pays $20.00; teams tied for the highest split what was paid.',
      ),
    ).toHaveLength(1);
  });

  it('names a single side-bet round in the singular', () => {
    render(
      <LateRowBetsView report={report({ sideBetRules: { amountPerTeam: 12.5, rounds: [7] } })} {...FIRST_TAB} />,
    );
    expect(screen.getByText(/^\$12\.50 per team in draft round 7\./)).toBeInTheDocument();
  });

  it('renders a table per side-bet round from sm up', () => {
    render(<LateRowBetsView report={report()} {...FIRST_TAB} />);
    expect(within(desktop()).getByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(within(desktop()).getByRole('heading', { name: 'Round 3' })).toBeInTheDocument();
  });

  it("shows each entry's team name from the entry itself, in the order the round arrives in", () => {
    render(<LateRowBetsView report={report({ teams: [] })} {...FIRST_TAB} />);
    const rows = within(roundTable(2)).getAllByRole('row').slice(1);
    const teamCells = rows.map((r) => within(r).getAllByRole('cell')[0]?.textContent);
    expect(teamCells).toEqual(['Aces', 'Birdies', 'Eagles']);
  });

  it("renders the totals table in the report's side-bet standings", () => {
    render(<LateRowBetsView report={report()} {...FIRST_TAB} />);
    const rows = within(totalsTable()).getAllByRole('row').slice(1);
    const teamCells = rows.map((r) => within(r).getAllByRole('cell')[0]?.textContent);
    expect(teamCells).toEqual(['Aces', 'Eagles', 'Birdies']);
  });

  it("displays the report's side-bet totals once, which are not a sum over its teams", () => {
    const disagreeing = report({
      cashTotals: { won: 55, lost: -66, net: -11 },
      sideBetTotals: { won: 9, lost: -2, net: 7 },
    });
    render(<LateRowBetsView report={disagreeing} {...FIRST_TAB} />);
    expect(screen.getByText('Won').nextSibling).toHaveTextContent('+$9.00');
    expect(screen.getByText('Lost').nextSibling).toHaveTextContent('\u2212$2.00');
    expect(screen.getByText('Net').nextSibling).toHaveTextContent('+$7.00');
    expect(phoneSection()).not.toContainElement(screen.getByText('Won'));
    expect(desktop()).not.toContainElement(screen.getByText('Won'));
  });

  it('says a season that stakes nothing on side bets has none, and shows no rules, tabs or totals', () => {
    const noSideBets = report({ sideBetRules: null, sideBetDetail: [] });
    render(<LateRowBetsView report={noSideBets} {...FIRST_TAB} />);
    expect(screen.getAllByText('This season has no late row bets.')).toHaveLength(1);
    expect(screen.queryByText(/per team in/)).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Late row round' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Total Late Row Bets/i })).not.toBeInTheDocument();
  });

  it("colours the all-rounds total by sign, and leaves each round's late row bet in the table ink", () => {
    render(<LateRowBetsView report={report()} {...FIRST_TAB} />);
    expect(within(totalsTable()).getByText('+$80.00')).toHaveClass('text-semantic-success');
    expect(within(totalsTable()).getByText('\u2212$60.00')).toHaveClass('text-semantic-danger');
    expect(within(roundTable(2)).getByText('+$40.00').className).not.toMatch(/text-semantic-|text-ink-soft/);
  });

  it('tones the won, lost and net chips by the sign they print, a zero Won gray', () => {
    render(<LateRowBetsView report={report({ sideBetTotals: { won: 0, lost: -2, net: -2 } })} {...FIRST_TAB} />);
    expect(screen.getByText('Won').nextSibling).toHaveClass('text-ink-soft');
    expect(screen.getByText('Lost').nextSibling).toHaveClass('text-semantic-danger');
    expect(screen.getByText('Net').nextSibling).toHaveClass('text-semantic-danger');
  });

  it('sets every column head in the shared column-header type', () => {
    render(<LateRowBetsView report={report()} {...FIRST_TAB} />);
    expectSharedColumnHeads(3);
  });

  describe('below sm', () => {
    function roundOf(round: number, teams: readonly ReportSideBetTeamEntry[]) {
      return { round, teams: [...teams] };
    }

    it('shows the round tabs and cards below sm, and the tables from sm up, with no bleed', () => {
      render(<LateRowBetsView report={report()} {...FIRST_TAB} />);

      expect(phoneSection()).toHaveClass('sm:hidden');
      expect(phoneSection().className).not.toMatch(/-mx-/);
      expect(desktop()).toHaveClass('hidden', 'sm:block');
      expect(desktop()).toContainElement(totalsTable());
    });

    it('offers a tab per round in the report and then Totals, the first round pressed by default', () => {
      const fourRounds = report({
        sideBetDetail: [5, 6, 7, 8].map((round) => roundOf(round, [entry('t-1', 'Aces', 'Scottie', 0, 0, null)])),
      });
      render(<LateRowBetsView report={fourRounds} {...FIRST_TAB} />);

      expect(chips().map((chip) => chip.textContent)).toEqual(['Rd 5', 'Rd 6', 'Rd 7', 'Rd 8', 'Totals']);
      expect(chips().map((chip) => chip.getAttribute('aria-pressed'))).toEqual(['true', 'false', 'false', 'false', 'false']);
    });

    it('reports the tapped tab', async () => {
      const onSelectTab = vi.fn();
      const user = userEvent.setup();
      render(<LateRowBetsView report={report()} selectedTab={null} onSelectTab={onSelectTab} />);

      await user.click(within(screen.getByRole('group', { name: 'Late row round' })).getByRole('button', { name: 'Rd 3' }));
      await user.click(within(screen.getByRole('group', { name: 'Late row round' })).getByRole('button', { name: 'Totals' }));

      expect(onSelectTab.mock.calls).toEqual([[{ kind: 'round', round: 3 }], [{ kind: 'totals' }]]);
    });

    it('shows the selected round', () => {
      render(<LateRowBetsView report={report()} selectedTab={{ kind: 'round', round: 3 }} onSelectTab={vi.fn()} />);

      expect(chips().map((chip) => chip.getAttribute('aria-pressed'))).toEqual(['false', 'true', 'false']);
      expect(nth(leaderCards(), 0)).toHaveTextContent('Leading round 3');
      expect(nth(leaderCards(), 0)).toHaveTextContent('Aces · Xander');
    });

    it('falls back to the first round when the selected one is no longer in the report', () => {
      render(<LateRowBetsView report={report()} selectedTab={{ kind: 'round', round: 9 }} onSelectTab={vi.fn()} />);

      expect(chips().map((chip) => chip.getAttribute('aria-pressed'))).toEqual(['true', 'false', 'false']);
      expect(nth(leaderCards(), 0)).toHaveTextContent('Leading round 2');
    });

    it("puts the leader in a gold card with the server's payout, and the rest under what each pays", () => {
      // A payout that is not amountPerTeam × losers, and an amount that is not payout ÷ losers.
      const settled = report({
        sideBetRules: { amountPerTeam: 15, rounds: [5] },
        sideBetDetail: [
          roundOf(5, [
            entry('t-1', 'Poczik', 'Tommy Fleetwood', 36, 40, 'won'),
            entry('t-2', 'Burrell', 'Brian Harman', 13.75, -15, 'lost'),
            entry('t-3', 'Kanham', 'Alex Noren', 11.8, -15, 'lost'),
            entry('t-4', 'Obaz', 'Taylor Pendrith', 0, -15, 'lost'),
          ]),
        ],
      });
      render(<LateRowBetsView report={settled} {...FIRST_TAB} />);

      const [leader] = leaderCards();
      expect(leaderCards()).toHaveLength(1);
      expect(leader).toHaveClass('bg-late-row-leader', 'border-l-4', 'border-gold');
      expect(leader).toHaveTextContent('Leading round 5');
      expect(leader).toHaveTextContent('Poczik · Tommy Fleetwood');
      expect(leader).toHaveTextContent('+$36.00 season earnings');
      expect(within(nth(leaderCards(), 0)).getByText('+$40.00')).toHaveClass('bg-money-up-fill');

      const chasing = phoneList('Chasing · each pays $15.00');
      const rows = within(chasing).getAllByRole('listitem');
      expect(rows.map((row) => row.textContent)).toEqual([
        'BurrellBrian Harman+$13.75',
        'KanhamAlex Noren+$11.80',
        'ObazTaylor Pendrith$0',
      ]);
      expect(within(nth(rows, 0)).getByText('+$13.75')).toHaveClass('text-ink');
      expect(within(nth(rows, 0)).getByText('+$13.75').className).not.toMatch(/bg-money/);
    });

    it("truncates a leader's long name inside the card, so the payout box stays on screen", () => {
      const longName = report({
        sideBetDetail: [roundOf(2, [entry('t-1', 'GALBRAITHWAITE-MACKENZIE', 'Christiaan Bezuidenhout', 300, 20, 'won')])],
      });
      render(<LateRowBetsView report={longName} {...FIRST_TAB} />);

      const name = within(nth(leaderCards(), 0)).getByText(/·/);
      expect(name).toHaveClass('truncate');
      expect(name.parentElement).toHaveClass('flex-1', 'min-w-0');
      expect(within(nth(leaderCards(), 0)).getByText('+$20.00')).toHaveClass('shrink-0');
    });

    it('gives every team tied for the lead a gold card, and never one that lost', () => {
      const tied = report({
        sideBetDetail: [
          roundOf(7, [
            entry('t-1', 'Aces', 'Scottie', 300, 20, 'won'),
            entry('t-2', 'Birdies', 'Rory', 300, 20, 'won'),
            entry('t-3', 'Eagles', 'Jon', 100, -20, 'lost'),
          ]),
        ],
      });
      render(<LateRowBetsView report={tied} {...FIRST_TAB} />);

      expect(leaderCards().map((card) => within(card).getByText(/·/).textContent)).toEqual([
        'Aces · Scottie',
        'Birdies · Rory',
      ]);
      const chasing = phoneList(/^Chasing/);
      expect(within(chasing).getByText('Eagles').closest('li')).not.toHaveClass('bg-late-row-leader');
    });

    it('reads the lead off the outcome, so a lost entry at the top of the order takes no gold card', () => {
      const outcomeOverRank = report({
        sideBetDetail: [
          roundOf(2, [
            entry('t-1', 'Aces', 'Scottie', 500, 40, 'lost'),
            entry('t-2', 'Birdies', 'Rory', 250, 40, 'won'),
          ]),
        ],
      });
      render(<LateRowBetsView report={outcomeOverRank} {...FIRST_TAB} />);

      expect(leaderCards()).toHaveLength(1);
      expect(nth(leaderCards(), 0)).toHaveTextContent('Birdies · Rory');
      expect(within(phoneList(/^Chasing/)).getByText('Aces')).toBeInTheDocument();
    });

    it('lists no one as chasing a round every team ties for', () => {
      const allTied = report({
        sideBetDetail: [
          roundOf(2, [entry('t-1', 'Aces', 'Scottie', 0, 0, 'won'), entry('t-2', 'Birdies', 'Rory', 0, 0, 'won')]),
        ],
      });
      render(<LateRowBetsView report={allTied} {...FIRST_TAB} />);

      expect(leaderCards()).toHaveLength(2);
      expect(within(nth(leaderCards(), 0)).getByText('$0')).toHaveClass('bg-money-even-fill');
      expect(within(phoneSection()).queryByText(/Chasing/)).not.toBeInTheDocument();
    });

    it('says nobody has earned in a round with no outcome yet, and lists every team without a stake', () => {
      const unsettled = report({
        sideBetDetail: [
          roundOf(6, [entry('t-1', 'Aces', 'Scottie', 0, 0, null), entry('t-2', 'Birdies', 'Rory', 0, 0, null)]),
        ],
      });
      render(<LateRowBetsView report={unsettled} {...FIRST_TAB} />);

      expect(within(phoneSection()).getByText('No team has earned in round 6 yet.')).toBeInTheDocument();
      expect(within(phoneSection()).queryByRole('list', { name: 'Leaders' })).not.toBeInTheDocument();
      expect(within(phoneSection()).queryByText(/each pays/)).not.toBeInTheDocument();
      const teams = within(phoneList('Round 6')).getAllByRole('listitem');
      expect(teams.map((row) => row.textContent)).toEqual(['AcesScottie$0', 'BirdiesRory$0']);
    });

    it('drops what each pays from the chasing label when the report sends no rules', () => {
      render(<LateRowBetsView report={report({ sideBetRules: null })} {...FIRST_TAB} />);

      expect(within(phoneSection()).getByRole('heading', { name: 'Chasing' })).toBeInTheDocument();
    });

    it("shows a card per team on the Totals tab, each team's total in a toned box", () => {
      render(<LateRowBetsView report={report()} selectedTab={{ kind: 'totals' }} onSelectTab={vi.fn()} />);

      expect(chips().map((chip) => chip.getAttribute('aria-pressed'))).toEqual(['false', 'false', 'true']);
      expect(within(phoneSection()).queryByRole('list', { name: 'Leaders' })).not.toBeInTheDocument();
      const cards = within(phoneList('Totals')).getAllByRole('listitem');
      expect(cards.map((card) => card.textContent)).toEqual(['Aces+$80.00', 'Eagles\u2212$20.00', 'Birdies\u2212$60.00']);
      expect(within(nth(cards, 0)).getByText('+$80.00')).toHaveClass('bg-money-up-fill');
      expect(within(nth(cards, 2)).getByText('\u2212$60.00')).toHaveClass('bg-money-down-fill');
    });
  });
});
