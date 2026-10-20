import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RosterTeam } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import RostersView from './RostersView';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const FIRST_TEAM = { selectedTeamId: null, onSelectTeam: () => undefined };

function grid(): HTMLElement {
  return screen.getByRole('table');
}

function pickCards(): HTMLElement[] {
  return within(screen.getByRole('list', { name: 'Picks' })).getAllByRole('listitem');
}

function pick(round: number, name: string, pct = 100, id = `g-${round}-${name}`) {
  return { round, golferName: name, ownershipPct: pct, golferId: id };
}

const aces: RosterTeam = {
  teamId: 't-1',
  teamNumber: 5,
  teamName: 'Aces',
  picks: [
    pick(1, 'Scottie Scheffler'),
    pick(2, 'Rory McIlroy', 60),
    pick(3, 'Xander Schauffele'),
    pick(5, 'Sahith Theegala'),
  ],
};
const birdies: RosterTeam = {
  teamId: 't-2',
  teamNumber: 8,
  teamName: 'Birdies',
  picks: [
    pick(1, 'Jon Rahm'),
    pick(2, 'Rory McIlroy', 40),
    pick(4, 'Viktor Hovland'),
  ],
};

describe('RostersView', () => {
  it('shows an empty state when there are no teams', () => {
    renderWithProviders(<RostersView teams={[]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    expect(screen.getByText(/No rosters uploaded yet/i)).toBeInTheDocument();
    expect(screen.getByText(/Use the Admin link at the bottom of the page/)).toBeInTheDocument();
  });

  it('renders team columns in the order provided', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const headerCells = screen.getAllByRole('columnheader');
    // first header is "RD", then one per team
    expect(headerCells[0]).toHaveTextContent('RD');
    expect(headerCells[1]).toHaveTextContent('Aces');
    expect(headerCells[2]).toHaveTextContent('Birdies');
  });

  it("labels each team column with the team's own number, not its position", () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const headerCells = screen.getAllByRole('columnheader');
    expect(headerCells[1]).toHaveTextContent('5');
    expect(headerCells[2]).toHaveTextContent('8');
  });

  it('falls back to column position when a team has no number', () => {
    const unnumbered: RosterTeam = { ...birdies, teamNumber: null };
    renderWithProviders(<RostersView teams={[aces, unnumbered]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const headerCells = screen.getAllByRole('columnheader');
    expect(headerCells[2]).toHaveTextContent('2');
  });

  it('renders a row for each of the 8 draft rounds', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const rows = screen.getAllByRole('row');
    // 1 header + 8 round rows
    expect(rows).toHaveLength(9);
  });

  it("tags exactly the season's late-row rounds", () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[2, 3]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const roundRows = screen.getAllByRole('row').slice(1);
    const tagged = roundRows.map((row) => within(row).queryByText('Late row') !== null);
    expect(tagged).toEqual([false, true, true, false, false, false, false, false]);
  });

  it('tags no round for a season with no side-bet rounds', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    expect(screen.queryByText('Late row')).not.toBeInTheDocument();
  });

  it('sets every team head on the navy band', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const [corner, ...teamHeads] = screen.getAllByRole('columnheader');
    expect(teamHeads).toHaveLength(2);
    for (const head of teamHeads) expect(head).toHaveClass('bg-header-band', 'text-on-fill');
    expect(corner).not.toHaveClass('bg-header-band');
  });

  it('renders an em dash for missing picks', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const rows = screen.getAllByRole('row').slice(1);
    // Aces has picks in rounds 1,2,3,5 — so rounds 4,6,7,8 should show the em dash
    const round4 = nth(rows, 3);
    expect(within(round4).getByText('—')).toBeInTheDocument();
  });

  it('shows ownership percentage only when less than 100%', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    // Rory (60%) should render the pct, Scheffler (100%) should not
    expect(within(grid()).getByText('60%')).toBeInTheDocument();
    expect(within(grid()).queryByText('100%')).not.toBeInTheDocument();
  });

  it('surfaces shared players across teams', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    const callout = screen.getByText(/Shared Players:/i).parentElement;
    if (!callout) throw new Error('the Shared Players label has no callout around it');
    expect(within(callout).getByText('Rory McIlroy')).toBeInTheDocument();
    expect(within(callout).getByText(/Aces/)).toBeInTheDocument();
    expect(within(callout).getByText(/Birdies/)).toBeInTheDocument();
  });

  it('makes pick names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} onGolferClick={onGolferClick} />, NO_PROVIDERS);
    await user.click(within(grid()).getByRole('button', { name: 'Scottie Scheffler' }));
    expect(onGolferClick).toHaveBeenCalledWith('g-1-Scottie Scheffler');
  });

  it('gives each pick name the tap-target height', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} onGolferClick={vi.fn()} />, NO_PROVIDERS);

    expect(within(grid()).getByRole('button', { name: 'Scottie Scheffler' })).toHaveClass(TAP_TARGET_CLASS);
  });

  it('renders pick names as plain text when no onGolferClick is provided', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    expect(screen.queryByRole('button', { name: 'Scottie Scheffler' })).not.toBeInTheDocument();
    expect(within(grid()).getByText('Scottie Scheffler')).toBeInTheDocument();
  });

  it('omits the shared players callout when there are none', () => {
    const unique: RosterTeam = {
      teamId: 't-9',
      teamNumber: 1,
      teamName: 'Solo',
      picks: [pick(1, 'Tiger Woods')],
    };
    renderWithProviders(<RostersView teams={[unique]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    expect(screen.queryByText(/Shared Players:/i)).not.toBeInTheDocument();
  });

  it('sets every column head in the shared column-header type', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);
    expectSharedColumnHeads(1);
  });

  describe('below sm', () => {
    function phoneSection(): HTMLElement {
      const section = screen.getByRole('group', { name: 'Team' }).parentElement;
      if (!section) throw new Error('the team strip has no section around it');
      return section;
    }

    it('shows one team as cards below sm, and the grid from sm up', () => {
      renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);

      expect(phoneSection()).toHaveClass('sm:hidden');
      expect(phoneSection()).toContainElement(screen.getByRole('list', { name: 'Picks' }));
      expect(phoneSection()).not.toHaveClass('-mx-3');
      expect(grid().parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('offers every team on the strip, the first pressed by default', () => {
      renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);

      const chips = within(screen.getByRole('group', { name: 'Team' })).getAllByRole('button');
      expect(chips.map((chip) => chip.textContent)).toEqual(['Aces', 'Birdies']);
      expect(chips.map((chip) => chip.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
    });

    it('shows the selected team', () => {
      renderWithProviders(
        <RostersView teams={[aces, birdies]} sideBetRounds={[]} selectedTeamId="t-2" onSelectTeam={vi.fn()} />,
        NO_PROVIDERS,
      );

      expect(within(phoneSection()).getByRole('heading')).toHaveTextContent('Birdies · 3 picks');
      expect(nth(pickCards(), 0)).toHaveTextContent('Jon Rahm');
    });

    it('falls back to the first team when the selected one is no longer in the season', () => {
      renderWithProviders(
        <RostersView teams={[aces, birdies]} sideBetRounds={[]} selectedTeamId="t-gone" onSelectTeam={vi.fn()} />,
        NO_PROVIDERS,
      );

      expect(within(phoneSection()).getByRole('heading')).toHaveTextContent('Aces · 4 picks');
      expect(within(screen.getByRole('group', { name: 'Team' })).getByRole('button', { name: 'Aces' })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
    });

    it('reports the tapped team', async () => {
      const onSelectTeam = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(
        <RostersView teams={[aces, birdies]} sideBetRounds={[]} selectedTeamId={null} onSelectTeam={onSelectTeam} />,
        NO_PROVIDERS,
      );

      await user.click(within(screen.getByRole('group', { name: 'Team' })).getByRole('button', { name: 'Birdies' }));

      expect(onSelectTeam).toHaveBeenCalledWith('t-2');
    });

    it('counts a single pick in the singular', () => {
      const solo: RosterTeam = { ...aces, picks: [pick(1, 'Tiger Woods')] };
      renderWithProviders(<RostersView teams={[solo]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);

      expect(within(phoneSection()).getByRole('heading')).toHaveTextContent(/^Aces · 1 pick$/);
    });

    it('sets a card per round, numbered in navy with round 1 never gold, a missing pick as a dash', () => {
      renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);

      const cards = pickCards();
      expect(cards).toHaveLength(8);
      const boxes = cards.map((card) => nth(Array.from(card.children), 0));
      expect(boxes.map((box) => box.textContent)).toEqual(['1', '2', '3', '4', '5', '6', '7', '8']);
      for (const box of boxes) {
        expect(box).toHaveClass('bg-position-fill');
        expect(box).not.toHaveClass('bg-gold');
      }
      expect(nth(cards, 3)).toHaveTextContent(/^4—$/);
    });

    it('puts a shared pick\'s ownership under its name', () => {
      renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);

      expect(within(nth(pickCards(), 1)).getByText('60%')).toHaveClass('text-ink-muted');
      expect(within(nth(pickCards(), 0)).queryByText('100%')).not.toBeInTheDocument();
    });

    it("tags exactly the season's late-row rounds", () => {
      renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[2, 3]} {...FIRST_TEAM} />, NO_PROVIDERS);

      const tagged = pickCards().map((card) => within(card).queryByText('Late row') !== null);
      expect(tagged).toEqual([false, true, true, false, false, false, false, false]);
    });

    it('opens a golfer from their card', async () => {
      const onGolferClick = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(
        <RostersView teams={[aces]} sideBetRounds={[]} {...FIRST_TEAM} onGolferClick={onGolferClick} />,
        NO_PROVIDERS,
      );

      const golfer = within(nth(pickCards(), 0)).getByRole('button', { name: 'Scottie Scheffler' });
      await user.click(golfer);

      expect(onGolferClick).toHaveBeenCalledWith('g-1-Scottie Scheffler');
      expect(golfer).toHaveClass(TAP_TARGET_CLASS);
    });

    it('lists the shared players once, under both layouts', () => {
      renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} {...FIRST_TEAM} />, NO_PROVIDERS);

      expect(screen.getAllByText(/Shared Players:/i)).toHaveLength(1);
    });
  });
});
