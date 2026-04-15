import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import RostersView from './RostersView';
import type { RosterTeam } from '@/api/types';

function pick(round: number, name: string, pct = 100, id = `g-${round}-${name}`) {
  return { round, golferName: name, ownershipPct: pct, golferId: id };
}

const aces: RosterTeam = {
  teamId: 't-1',
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
  teamName: 'Birdies',
  picks: [
    pick(1, 'Jon Rahm'),
    pick(2, 'Rory McIlroy', 40),
    pick(4, 'Viktor Hovland'),
  ],
};

describe('RostersView', () => {
  it('shows an empty state when there are no teams', () => {
    render(<RostersView teams={[]} />);
    expect(screen.getByText(/No rosters uploaded yet/i)).toBeInTheDocument();
  });

  it('renders team columns in the order provided', () => {
    render(<RostersView teams={[aces, birdies]} />);
    const headerCells = screen.getAllByRole('columnheader');
    // first header is "RD", then one per team
    expect(headerCells[0]).toHaveTextContent('RD');
    expect(headerCells[1]).toHaveTextContent('Aces');
    expect(headerCells[2]).toHaveTextContent('Birdies');
  });

  it('renders a row for each of the 8 draft rounds', () => {
    render(<RostersView teams={[aces, birdies]} />);
    const rows = screen.getAllByRole('row');
    // 1 header + 8 round rows
    expect(rows).toHaveLength(9);
  });

  it('renders an em dash for missing picks', () => {
    render(<RostersView teams={[aces]} />);
    const rows = screen.getAllByRole('row').slice(1);
    // Aces has picks in rounds 1,2,3,5 — so rounds 4,6,7,8 should show the em dash
    const round4 = rows[3];
    expect(within(round4).getByText('—')).toBeInTheDocument();
  });

  it('shows ownership percentage only when less than 100%', () => {
    render(<RostersView teams={[aces]} />);
    // Rory (60%) should render the pct, Scheffler (100%) should not
    expect(screen.getByText('60%')).toBeInTheDocument();
    expect(screen.queryByText('100%')).not.toBeInTheDocument();
  });

  it('surfaces shared players across teams', () => {
    render(<RostersView teams={[aces, birdies]} />);
    const callout = screen.getByText(/Shared Players:/i).parentElement!;
    expect(within(callout).getByText('Rory McIlroy')).toBeInTheDocument();
    expect(within(callout).getByText(/Aces/)).toBeInTheDocument();
    expect(within(callout).getByText(/Birdies/)).toBeInTheDocument();
  });

  it('omits the shared players callout when there are none', () => {
    const unique: RosterTeam = {
      teamId: 't-9',
      teamName: 'Solo',
      picks: [pick(1, 'Tiger Woods')],
    };
    render(<RostersView teams={[unique]} />);
    expect(screen.queryByText(/Shared Players:/i)).not.toBeInTheDocument();
  });
});
