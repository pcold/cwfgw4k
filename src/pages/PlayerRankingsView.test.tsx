import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PlayerRankingsView from './PlayerRankingsView';
import type { PlayerRankingsRow } from './playerRankingsModel';

function r(overrides: Partial<PlayerRankingsRow>): PlayerRankingsRow {
  return {
    key: 'g:g-1',
    golferId: 'g-1',
    name: 'Scottie Scheffler',
    topTens: 1,
    totalEarnings: 18,
    teamName: 'Aces',
    draftRound: 1,
    ...overrides,
  };
}

describe('PlayerRankingsView', () => {
  it('shows an empty state when no players qualify', () => {
    render(<PlayerRankingsView players={[]} />);
    expect(screen.getByText(/No players with a top 10 finish yet/i)).toBeInTheDocument();
  });

  it('renders a row per player with name, top 10s, total $, team, and round', () => {
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:g-1', name: 'Scottie Scheffler', topTens: 3, totalEarnings: 50, teamName: 'Aces', draftRound: 1 }),
          r({ key: 'u:Phil', golferId: null, name: 'P. Mickelson', topTens: 1, totalEarnings: 8, teamName: null, draftRound: null }),
        ]}
      />,
    );

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);

    const first = within(rows[0]);
    const firstCells = first.getAllByRole('cell');
    expect(firstCells[0]).toHaveTextContent('1');
    expect(firstCells[1]).toHaveTextContent('Scottie Scheffler');
    expect(firstCells[2]).toHaveTextContent('3');
    expect(firstCells[3]).toHaveTextContent('$50');
    expect(firstCells[4]).toHaveTextContent('Aces');
    expect(firstCells[5]).toHaveTextContent('1');

    const second = within(rows[1]);
    expect(second.getByText('P. Mickelson')).toBeInTheDocument();
    expect(second.getByText('undrafted')).toBeInTheDocument();
    expect(second.getByText('—')).toBeInTheDocument();
  });

  it('shows the live overlay note when live is true', () => {
    render(<PlayerRankingsView players={[r({})]} live />);
    expect(screen.getByText(/live overlay on/i)).toBeInTheDocument();
  });

  it('makes drafted player names clickable when onGolferClick is provided', async () => {
    const onGolferClick = vi.fn();
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[r({ golferId: 'g-42', name: 'Clickable' })]}
        onGolferClick={onGolferClick}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'Clickable' }));
    expect(onGolferClick).toHaveBeenCalledWith('g-42');
  });

  it('does not render undrafted players as buttons even when onGolferClick is provided', () => {
    render(
      <PlayerRankingsView
        players={[r({ key: 'u:Phil', golferId: null, name: 'P. Mickelson' })]}
        onGolferClick={vi.fn()}
      />,
    );
    expect(screen.queryByRole('button', { name: 'P. Mickelson' })).not.toBeInTheDocument();
    expect(screen.getByText('P. Mickelson')).toBeInTheDocument();
  });

  it('renders names as plain text when no onGolferClick is provided', () => {
    render(<PlayerRankingsView players={[r({})]} />);
    expect(screen.queryByRole('button', { name: 'Scottie Scheffler' })).not.toBeInTheDocument();
    expect(screen.getByText('Scottie Scheffler')).toBeInTheDocument();
  });
});
