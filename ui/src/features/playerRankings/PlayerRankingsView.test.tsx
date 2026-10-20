import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PlayerRankingsRow } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { MONEY_FIGURE_CLASS } from '@/shared/components/MoneyCell';
import PlayerRankingsView from './PlayerRankingsView';

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

function playerTable(): HTMLElement {
  return screen.getByRole('table');
}

function tableRows(): HTMLElement[] {
  return within(playerTable()).getAllByRole('row').slice(1);
}

function phoneCards(): HTMLElement {
  return screen.getByRole('list', { name: 'Players' });
}

function phoneCard(name: string): HTMLElement {
  const card = within(phoneCards()).getByText(name).closest('li');
  if (!card) throw new Error(`no card for ${name}`);
  return card;
}

function phoneDetailLine(name: string): HTMLElement {
  return nth(Array.from(phoneCard(name).querySelectorAll<HTMLElement>('.text-ink-muted')), 0);
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

    const rows = tableRows();
    expect(rows).toHaveLength(2);

    const first = within(nth(rows, 0));
    const firstCells = first.getAllByRole('cell');
    expect(firstCells[0]).toHaveTextContent('1');
    expect(firstCells[1]).toHaveTextContent('Scottie Scheffler');
    expect(firstCells[2]).toHaveTextContent('3');
    expect(firstCells[3]).toHaveTextContent('$50.00');
    expect(firstCells[4]).toHaveTextContent('Aces');
    expect(firstCells[5]).toHaveTextContent('1');

    const second = within(nth(rows, 1));
    expect(second.getByText('P. Mickelson')).toBeInTheDocument();
    expect(second.getByText('undrafted')).toBeInTheDocument();
    expect(second.getByText('—')).toBeInTheDocument();
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
    await user.click(within(playerTable()).getByRole('button', { name: 'Clickable' }));
    expect(onGolferClick).toHaveBeenCalledWith('g-42');
  });

  it('gives each drafted player name the tap-target height', () => {
    render(
      <PlayerRankingsView
        players={[r({ golferId: 'g-42', name: 'Clickable' })]}
        onGolferClick={vi.fn()}
      />,
    );

    expect(within(playerTable()).getByRole('button', { name: 'Clickable' })).toHaveClass(TAP_TARGET_CLASS);
  });

  it('does not render undrafted players as buttons even when onGolferClick is provided', () => {
    render(
      <PlayerRankingsView
        players={[r({ key: 'u:Phil', golferId: null, name: 'P. Mickelson' })]}
        onGolferClick={vi.fn()}
      />,
    );
    expect(screen.queryByRole('button', { name: 'P. Mickelson' })).not.toBeInTheDocument();
    expect(within(playerTable()).getByText('P. Mickelson')).toBeInTheDocument();
  });

  it('renders names as plain text when no onGolferClick is provided', () => {
    render(<PlayerRankingsView players={[r({})]} />);
    expect(screen.queryByRole('button', { name: 'Scottie Scheffler' })).not.toBeInTheDocument();
    expect(within(playerTable()).getByText('Scottie Scheffler')).toBeInTheDocument();
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

    expect(tableRows()).toHaveLength(3);

    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Aces');

    const rows = tableRows();
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Scottie Scheffler')).toBeInTheDocument();
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

    const rows = tableRows();
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('P. Mickelson')).toBeInTheDocument();
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
    expect(tableRows()).toHaveLength(1);

    await user.selectOptions(screen.getByLabelText(/Filter by team/i), 'Team');
    expect(tableRows()).toHaveLength(1);
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

    const select = screen.getByLabelText<HTMLSelectElement>(/Filter by team/i);
    const teamOptionLabels = Array.from(select.options)
      .map((o) => o.textContent)
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

    const rows = tableRows();
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('R3 Guy')).toBeInTheDocument();
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

    const rows = tableRows();
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Not Drafted')).toBeInTheDocument();
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

    const select = screen.getByLabelText<HTMLSelectElement>(/Filter by draft round/i);
    const numericLabels = Array.from(select.options)
      .map((o) => o.textContent)
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

    const rows = tableRows();
    expect(rows).toHaveLength(1);
    expect(within(nth(rows, 0)).getByText('Aces R1')).toBeInTheDocument();
  });

  it('tones each season Earnings total by the sign it prints, a zero gray', () => {
    render(
      <PlayerRankingsView
        players={[
          r({ key: 'g:g-1', name: 'Earner', totalEarnings: 50 }),
          r({ key: 'g:g-2', golferId: 'g-2', name: 'Blank', totalEarnings: 0.004 }),
        ]}
      />,
    );
    expect(within(playerTable()).getByText('+$50.00')).toHaveClass('text-semantic-success');
    expect(within(playerTable()).getByText('$0')).toHaveClass('text-ink-soft');
  });

  it('sets every column head in the shared column-header type', () => {
    render(<PlayerRankingsView players={[r({})]} />);
    expectSharedColumnHeads(1);
  });

  describe('on a phone', () => {
    const roster = [
      r({ key: 'g:g-1', golferId: 'g-1', name: 'Scottie Scheffler', topTens: 4, totalEarnings: 50, teamName: 'Aces', draftRound: 1 }),
      r({ key: 'g:g-2', golferId: 'g-2', name: 'Rory McIlroy', topTens: 1, totalEarnings: 12, teamName: 'Birdies', draftRound: 3 }),
      r({ key: 'u:Phil', golferId: null, name: 'P. Mickelson', topTens: 1, totalEarnings: 0.004, teamName: null, draftRound: null }),
    ];

    it('shows the cards and their filters below sm and the table from sm up', () => {
      render(<PlayerRankingsView players={roster} />);

      const phoneLayout = phoneCards().parentElement;
      expect(phoneLayout).toHaveClass('sm:hidden');
      expect(phoneLayout).toContainElement(screen.getByRole('combobox', { name: 'Team' }));
      expect(playerTable().parentElement).toHaveClass('hidden', 'sm:block');
    });

    it('sets a card per player in the order the server sent them', () => {
      render(<PlayerRankingsView players={roster} />);

      const names = within(phoneCards())
        .getAllByRole('listitem')
        .map((card) => nth(Array.from(card.querySelectorAll('.font-display')), 1).textContent);
      expect(names).toEqual(['Scottie Scheffler', 'Rory McIlroy', 'P. Mickelson']);
    });

    it('boxes the first position in gold and every other in navy', () => {
      render(<PlayerRankingsView players={roster} />);

      const boxes = within(phoneCards())
        .getAllByRole('listitem')
        .map((card) => card.firstElementChild);
      expect(boxes.map((box) => box?.textContent)).toEqual(['1', '2', '3']);
      expect(nth(boxes, 0)).toHaveClass('bg-gold');
      expect(nth(boxes, 1)).toHaveClass('bg-position-fill');
      expect(nth(boxes, 1)).not.toHaveClass('bg-gold');
      expect(nth(boxes, 2)).toHaveClass('bg-position-fill');
    });

    it("keeps the golfer's name as the server spells it, a tap-target button", async () => {
      const onGolferClick = vi.fn();
      const user = userEvent.setup();
      render(<PlayerRankingsView players={roster} onGolferClick={onGolferClick} />);

      const name = within(phoneCards()).getByRole('button', { name: 'Scottie Scheffler' });
      expect(name).toHaveClass(TAP_TARGET_CLASS);
      expect(name.parentElement).toHaveClass('normal-case');
      expect(name.parentElement).not.toHaveClass('uppercase');
      await user.click(name);
      expect(onGolferClick).toHaveBeenCalledWith('g-1');
      expect(within(phoneCard('P. Mickelson')).queryByRole('button')).not.toBeInTheDocument();
    });

    it('prints the team, the draft round and the top-10 count under the name', () => {
      render(<PlayerRankingsView players={roster} />);

      expect(phoneDetailLine('Scottie Scheffler')).toHaveTextContent(/^Aces · Rd 1 · 4 top 10s$/);
      expect(within(phoneDetailLine('Scottie Scheffler')).getByText('Aces')).toHaveClass('uppercase');
    });

    it('counts a single top 10 in the singular', () => {
      render(<PlayerRankingsView players={roster} />);

      expect(phoneDetailLine('Rory McIlroy')).toHaveTextContent(/^Birdies · Rd 3 · 1 top 10$/);
    });

    it('names an undrafted player undrafted, with no round', () => {
      render(<PlayerRankingsView players={roster} />);

      expect(phoneDetailLine('P. Mickelson')).toHaveTextContent(/^Undrafted · 1 top 10$/);
    });

    it('leaves the round out for a drafted player the server sends no round for', () => {
      render(<PlayerRankingsView players={[r({ name: 'Roundless', topTens: 2, teamName: 'Aces', draftRound: null })]} />);

      expect(phoneDetailLine('Roundless')).toHaveTextContent(/^Aces · 2 top 10s$/);
    });

    it('sets the total as a plain figure toned by its sign, with no fill', () => {
      render(<PlayerRankingsView players={roster} />);

      const earner = within(phoneCard('Scottie Scheffler')).getByText('+$50.00');
      expect(earner).toHaveClass(...MONEY_FIGURE_CLASS.split(' '), 'text-semantic-success');
      expect(earner.className).not.toMatch(/\bbg-/);
      expect(within(phoneCard('P. Mickelson')).getByText('$0')).toHaveClass('text-ink-soft');
    });

    it('offers every team and round from its phone selects, all of them first', () => {
      render(<PlayerRankingsView players={roster} />);

      const team = screen.getByRole<HTMLSelectElement>('combobox', { name: 'Team' });
      const round = screen.getByRole<HTMLSelectElement>('combobox', { name: 'Draft round' });
      expect(Array.from(team.options).map((o) => o.textContent)).toEqual(['All teams', 'Aces', 'Birdies', 'Undrafted']);
      expect(Array.from(round.options).map((o) => o.textContent)).toEqual(['All rounds', '1', '3', 'Undrafted']);
      expect(team).toHaveClass(TAP_TARGET_CLASS);
      expect(round).toHaveClass(TAP_TARGET_CLASS);
    });

    it('filters both the cards and the table by the phone team select', async () => {
      const user = userEvent.setup();
      render(<PlayerRankingsView players={roster} />);

      await user.selectOptions(screen.getByRole('combobox', { name: 'Team' }), 'Birdies');

      expect(within(phoneCards()).getAllByRole('listitem')).toHaveLength(1);
      expect(within(phoneCards()).getByText('Rory McIlroy')).toBeInTheDocument();
      expect(tableRows()).toHaveLength(1);
      expect(within(nth(tableRows(), 0)).getByText('Rory McIlroy')).toBeInTheDocument();
      expect(screen.getByLabelText<HTMLSelectElement>(/Filter by team/i).value).toBe('Birdies');
    });

    it('filters both the cards and the table by the phone round select', async () => {
      const user = userEvent.setup();
      render(<PlayerRankingsView players={roster} />);

      await user.selectOptions(screen.getByRole('combobox', { name: 'Draft round' }), 'Undrafted');

      expect(within(phoneCards()).getAllByRole('listitem')).toHaveLength(1);
      expect(within(phoneCards()).getByText('P. Mickelson')).toBeInTheDocument();
      expect(tableRows()).toHaveLength(1);
      expect(within(nth(tableRows(), 0)).getByText('P. Mickelson')).toBeInTheDocument();
    });

    it('says no player matches in place of the cards when the filters leave none', async () => {
      const user = userEvent.setup();
      render(<PlayerRankingsView players={roster} />);

      await user.selectOptions(screen.getByRole('combobox', { name: 'Team' }), 'Aces');
      await user.selectOptions(screen.getByRole('combobox', { name: 'Draft round' }), '3');

      expect(screen.queryByRole('list', { name: 'Players' })).not.toBeInTheDocument();
      const phoneMessage = screen
        .getAllByText('No players match this filter.')
        .filter((message) => !playerTable().contains(message));
      expect(phoneMessage).toHaveLength(1);
      expect(nth(phoneMessage, 0).parentElement).toHaveClass('sm:hidden');
    });
  });
});
