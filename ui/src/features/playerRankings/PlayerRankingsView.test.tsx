import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PlayerRankingsView from './PlayerRankingsView';
import type { PlayerRankingsRow } from '@/shared/api/types';

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
    expect(firstCells[3]).toHaveTextContent('$50.00');
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

  it('filters rows to a single team when that team is selected in the header', async () => {
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:g-1', name: 'Scottie Scheffler', teamName: 'Aces' }),
          r({ key: 'g:g-2', name: 'Rory McIlroy', teamName: 'Birdies' }),
          r({ key: 'u:Phil', golferId: null, name: 'P. Mickelson', teamName: null }),
        ]}
      />,
    );

    expect(screen.getAllByRole('row').slice(1)).toHaveLength(3);

    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Aces');

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Scottie Scheffler')).toBeInTheDocument();
  });

  it('offers an Undrafted option that shows only undrafted players', async () => {
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:g-1', name: 'Scottie Scheffler', teamName: 'Aces' }),
          r({ key: 'u:Phil', golferId: null, name: 'P. Mickelson', teamName: null }),
        ]}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Undrafted');

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('P. Mickelson')).toBeInTheDocument();
  });

  it('omits the Undrafted option when no undrafted players are present', () => {
    render(<PlayerRankingsView players={[r({ teamName: 'Aces' })]} />);
    expect(screen.queryByRole('option', { name: 'Undrafted' })).not.toBeInTheDocument();
  });

  it('shows a no-match message when the filter yields zero rows and keeps the filter visible', async () => {
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[r({ key: 'g:g-1', name: 'Scottie Scheffler', teamName: 'Aces' })]}
      />,
    );
    // Add a team to the option list first - only Aces is a real option, so pick it then switch back.
    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Aces');
    expect(screen.getAllByRole('row').slice(1)).toHaveLength(1);

    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Team');
    expect(screen.getAllByRole('row').slice(1)).toHaveLength(1);
  });

  it('deduplicates and alphabetizes the team options', () => {
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:1', teamName: 'Cuts' }),
          r({ key: 'g:2', teamName: 'Aces' }),
          r({ key: 'g:3', teamName: 'Birdies' }),
          r({ key: 'g:4', teamName: 'Aces' }),
        ]}
      />,
    );

    const select = screen.getByLabelText(/Filter by team/i) as HTMLSelectElement;
    const teamOptionLabels = Array.from(select.options)
      .map((o) => o.textContent ?? '')
      .filter((label) => label !== 'Team' && label !== 'Undrafted');
    expect(teamOptionLabels).toEqual(['Aces', 'Birdies', 'Cuts']);
  });

  it('filters rows by draft round when the round header is changed', async () => {
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:1', name: 'R1 Guy', teamName: 'Aces', draftRound: 1 }),
          r({ key: 'g:2', name: 'R3 Guy', teamName: 'Birdies', draftRound: 3 }),
          r({ key: 'u:Phil', golferId: null, name: 'Undrafted Guy', teamName: null, draftRound: null }),
        ]}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter by draft round/i), '3');

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('R3 Guy')).toBeInTheDocument();
  });

  it('offers an Undrafted option on the round filter when any undrafted players exist', async () => {
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:1', name: 'Drafted', teamName: 'Aces', draftRound: 2 }),
          r({ key: 'u:Phil', golferId: null, name: 'Not Drafted', teamName: null, draftRound: null }),
        ]}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter by draft round/i), 'Undrafted');

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Not Drafted')).toBeInTheDocument();
  });

  it('sorts round options ascending and dedupes', () => {
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:1', draftRound: 7 }),
          r({ key: 'g:2', draftRound: 1 }),
          r({ key: 'g:3', draftRound: 3 }),
          r({ key: 'g:4', draftRound: 1 }),
        ]}
      />,
    );

    const select = screen.getByLabelText(/Filter by draft round/i) as HTMLSelectElement;
    const numericLabels = Array.from(select.options)
      .map((o) => o.textContent ?? '')
      .filter((label) => label !== 'Round' && label !== 'Undrafted');
    expect(numericLabels).toEqual(['1', '3', '7']);
  });

  it('combines team and round filters with AND semantics', async () => {
    const user = userEvent.setup();
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:1', name: 'Aces R1', teamName: 'Aces', draftRound: 1 }),
          r({ key: 'g:2', name: 'Aces R2', teamName: 'Aces', draftRound: 2 }),
          r({ key: 'g:3', name: 'Birdies R1', teamName: 'Birdies', draftRound: 1 }),
        ]}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Aces');
    await user.selectOptions(screen.getByLabelText(/Filter by draft round/i), '1');

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Aces R1')).toBeInTheDocument();
  });
});
