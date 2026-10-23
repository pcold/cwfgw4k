import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { makeRoundScorecard } from '@/shared/test/fixtures';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import LeaderboardScorecard from './LeaderboardScorecard';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const PARS_ON_NINE = [4, 4, 4, 4, 4, 4, 4, 4, 4];

function nine(round: number, half: 'front' | 'back'): HTMLElement {
  return screen.getByRole('table', { name: `Round ${round}, ${half} nine` });
}

function cellsOf(table: HTMLElement, label: string): HTMLElement[] {
  const tableRow = within(table).getByRole('rowheader', { name: label }).closest('tr');
  if (!tableRow) throw new Error(`no ${label} row`);
  return within(tableRow).getAllByRole('cell');
}

function rowOf(table: HTMLElement, label: string): string[] {
  return cellsOf(table, label).map((cell) => cell.textContent);
}

describe('LeaderboardScorecard', () => {
  it('heads each nine with its hole numbers and sums, and sets par then strokes under them', () => {
    renderWithProviders(
      <LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [...PARS_ON_NINE, ...PARS_ON_NINE])]} />,
      NO_PROVIDERS,
    );

    const front = nine(1, 'front');
    const back = nine(1, 'back');
    expect(within(front).getAllByRole('columnheader').map((head) => head.textContent)).toEqual(
      ['Hole', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'Out'],
    );
    expect(within(back).getAllByRole('columnheader').map((head) => head.textContent)).toEqual(
      ['Hole', '10', '11', '12', '13', '14', '15', '16', '17', '18', 'In', 'Tot'],
    );
    expect(rowOf(front, 'Par')).toEqual([...PARS_ON_NINE.map(String), '36']);
    expect(rowOf(front, 'Score')).toEqual([...PARS_ON_NINE.map(String), '36']);
    expect(rowOf(back, 'Par')).toEqual([...PARS_ON_NINE.map(String), '36', '72']);
    expect(rowOf(back, 'Score')).toEqual([...PARS_ON_NINE.map(String), '36', '72']);
  });

  it('leaves a par sum blank when the server sends none', () => {
    const scorecard = { ...makeRoundScorecard(1, [4]), backPar: null, totalPar: null };
    renderWithProviders(<LeaderboardScorecard id="card" scorecards={[scorecard]} />, NO_PROVIDERS);

    expect(rowOf(nine(1, 'front'), 'Par').at(-1)).toBe('36');
    expect(rowOf(nine(1, 'back'), 'Par').slice(-2)).toEqual(['', '']);
  });

  it('marks a hole by its score to par, outlining one off and filling two or more, round under and square over', () => {
    renderWithProviders(
      <LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [2, 3, 4, 5, 6, 7])]} />,
      NO_PROVIDERS,
    );

    expect(rowOf(nine(1, 'front'), 'Score').slice(0, 6)).toEqual([
      '2, Eagle or better',
      '3, Birdie',
      '4',
      '5, Bogey',
      '6, Double bogey or worse',
      '7, Double bogey or worse',
    ]);
    const marks = cellsOf(nine(1, 'front'), 'Score').map((cell) => cell.firstElementChild);
    expect(marks[0]).toHaveClass('rounded-full', 'bg-score-eagle');
    expect(marks[1]).toHaveClass('rounded-full', 'border-score-birdie');
    expect(marks[2]).toBeNull();
    expect(marks[3]).toHaveClass('rounded-sm', 'border-score-bogey');
    expect(marks[4]).toHaveClass('rounded-sm', 'bg-score-double-bogey');
  });

  it('leaves an unplayed hole and a nine not yet begun blank', () => {
    renderWithProviders(<LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [4, 4])]} />, NO_PROVIDERS);

    expect(rowOf(nine(1, 'front'), 'Score')).toEqual(['4', '4', '', '', '', '', '', '', '', '8']);
    expect(rowOf(nine(1, 'back'), 'Score')).toEqual(['', '', '', '', '', '', '', '', '', '', '8']);
  });

  it('opens on the latest round and shows the round picked', async () => {
    renderWithProviders(
      <LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [3]), makeRoundScorecard(2, [5])]} />,
      NO_PROVIDERS,
    );
    const picker = screen.getByRole('group', { name: 'Round' });

    expect(within(picker).getByRole('button', { name: 'R2' })).toHaveAttribute('aria-pressed', 'true');
    expect(rowOf(nine(2, 'front'), 'Score')[0]).toBe('5, Bogey');

    await userEvent.click(within(picker).getByRole('button', { name: 'R1' }));

    expect(within(picker).getByRole('button', { name: 'R1' })).toHaveAttribute('aria-pressed', 'true');
    expect(rowOf(nine(1, 'front'), 'Score')[0]).toBe('3, Birdie');
  });

  it('offers no round picker for a single round', () => {
    renderWithProviders(<LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [4])]} />, NO_PROVIDERS);

    expect(screen.queryByRole('group', { name: 'Round' })).not.toBeInTheDocument();
  });

  it('stacks the back nine under the front until its own card is wide enough for both on one line', () => {
    renderWithProviders(<LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [4])]} />, NO_PROVIDERS);

    const nines = nine(1, 'front').parentElement;
    expect(nines).toHaveClass('flex-col', '@min-[36rem]:flex-row');
    expect(nines?.parentElement).toHaveClass('overflow-x-auto');
    expect(nines?.parentElement?.parentElement).toHaveClass('@container');
    expect(nines?.parentElement?.parentElement).toHaveAttribute('id', 'card');
  });

  it('keys the marks in a legend', () => {
    renderWithProviders(<LeaderboardScorecard id="card" scorecards={[makeRoundScorecard(1, [4])]} />, NO_PROVIDERS);

    const legend = screen.getByRole('list', { name: 'Score marks' });
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Eagle or better',
      'Birdie',
      'Bogey',
      'Double bogey or worse',
    ]);
  });
});
