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
    renderWithProviders(<RostersView teams={[]} sideBetRounds={[]} />, NO_PROVIDERS);
    expect(screen.getByText(/No rosters uploaded yet/i)).toBeInTheDocument();
    expect(screen.getByText(/Use the Admin link at the bottom of the page/)).toBeInTheDocument();
  });

  it('renders team columns in the order provided', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} />, NO_PROVIDERS);
    const headerCells = screen.getAllByRole('columnheader');
    // first header is "RD", then one per team
    expect(headerCells[0]).toHaveTextContent('RD');
    expect(headerCells[1]).toHaveTextContent('Aces');
    expect(headerCells[2]).toHaveTextContent('Birdies');
  });

  it("labels each team column with the team's own number, not its position", () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} />, NO_PROVIDERS);
    const headerCells = screen.getAllByRole('columnheader');
    expect(headerCells[1]).toHaveTextContent('5');
    expect(headerCells[2]).toHaveTextContent('8');
  });

  it('falls back to column position when a team has no number', () => {
    const unnumbered: RosterTeam = { ...birdies, teamNumber: null };
    renderWithProviders(<RostersView teams={[aces, unnumbered]} sideBetRounds={[]} />, NO_PROVIDERS);
    const headerCells = screen.getAllByRole('columnheader');
    expect(headerCells[2]).toHaveTextContent('2');
  });

  it('renders a row for each of the 8 draft rounds', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} />, NO_PROVIDERS);
    const rows = screen.getAllByRole('row');
    // 1 header + 8 round rows
    expect(rows).toHaveLength(9);
  });

  it("tags exactly the season's late-row rounds", () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[2, 3]} />, NO_PROVIDERS);
    const roundRows = screen.getAllByRole('row').slice(1);
    const tagged = roundRows.map((row) => within(row).queryByText('Late row') !== null);
    expect(tagged).toEqual([false, true, true, false, false, false, false, false]);
  });

  it('tags no round for a season with no side-bet rounds', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} />, NO_PROVIDERS);
    expect(screen.queryByText('Late row')).not.toBeInTheDocument();
  });

  it('sets every team head on the navy band', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} />, NO_PROVIDERS);
    const [corner, ...teamHeads] = screen.getAllByRole('columnheader');
    expect(teamHeads).toHaveLength(2);
    for (const head of teamHeads) expect(head).toHaveClass('bg-header-band', 'text-on-fill');
    expect(corner).not.toHaveClass('bg-header-band');
  });

  it('renders an em dash for missing picks', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} />, NO_PROVIDERS);
    const rows = screen.getAllByRole('row').slice(1);
    // Aces has picks in rounds 1,2,3,5 — so rounds 4,6,7,8 should show the em dash
    const round4 = nth(rows, 3);
    expect(within(round4).getByText('—')).toBeInTheDocument();
  });

  it('shows ownership percentage only when less than 100%', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} />, NO_PROVIDERS);
    // Rory (60%) should render the pct, Scheffler (100%) should not
    expect(screen.getByText('60%')).toBeInTheDocument();
    expect(screen.queryByText('100%')).not.toBeInTheDocument();
  });

  it('surfaces shared players across teams', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} />, NO_PROVIDERS);
    const callout = screen.getByText(/Shared Players:/i).parentElement;
    if (!callout) throw new Error('the Shared Players label has no callout around it');
    expect(within(callout).getByText('Rory McIlroy')).toBeInTheDocument();
    expect(within(callout).getByText(/Aces/)).toBeInTheDocument();
    expect(within(callout).getByText(/Birdies/)).toBeInTheDocument();
  });

  it('makes pick names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} onGolferClick={onGolferClick} />, NO_PROVIDERS);
    await user.click(screen.getByRole('button', { name: 'Scottie Scheffler' }));
    expect(onGolferClick).toHaveBeenCalledWith('g-1-Scottie Scheffler');
  });

  it('gives each pick name the tap-target height', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} onGolferClick={vi.fn()} />, NO_PROVIDERS);

    expect(screen.getByRole('button', { name: 'Scottie Scheffler' })).toHaveClass(TAP_TARGET_CLASS);
  });

  it('renders pick names as plain text when no onGolferClick is provided', () => {
    renderWithProviders(<RostersView teams={[aces]} sideBetRounds={[]} />, NO_PROVIDERS);
    expect(screen.queryByRole('button', { name: 'Scottie Scheffler' })).not.toBeInTheDocument();
    expect(screen.getByText('Scottie Scheffler')).toBeInTheDocument();
  });

  it('omits the shared players callout when there are none', () => {
    const unique: RosterTeam = {
      teamId: 't-9',
      teamNumber: 1,
      teamName: 'Solo',
      picks: [pick(1, 'Tiger Woods')],
    };
    renderWithProviders(<RostersView teams={[unique]} sideBetRounds={[]} />, NO_PROVIDERS);
    expect(screen.queryByText(/Shared Players:/i)).not.toBeInTheDocument();
  });

  it('sets every column head in the shared column-header type', () => {
    renderWithProviders(<RostersView teams={[aces, birdies]} sideBetRounds={[]} />, NO_PROVIDERS);
    expectSharedColumnHeads(1);
  });
});
