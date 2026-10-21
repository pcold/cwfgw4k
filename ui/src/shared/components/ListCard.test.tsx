import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { nth } from '@/shared/test/nth';
import ListCard, { CARD_LIST_CLASS } from './ListCard';
import MoneyCell from './MoneyCell';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

function renderInList(card: ReactElement): HTMLElement {
  renderWithProviders(<ol className={CARD_LIST_CLASS}>{card}</ol>, NO_PROVIDERS);
  return screen.getByRole('listitem');
}

describe('ListCard', () => {
  it('sets the position box, the name over its detail line, then the total, in that order', () => {
    const card = renderInList(
      <ListCard
        position={2}
        name="Aces"
        nameCase="team"
        detail="Sub +$120.00"
        total={<MoneyCell display="+$150" shape="card-end" />}
      />,
    );

    const [position, middle, total] = Array.from(card.children);
    expect(position).toHaveTextContent(/^2$/);
    expect(position).toHaveClass('bg-position-fill');
    expect(nth(Array.from(middle?.children ?? []), 0)).toHaveTextContent(/^Aces$/);
    expect(nth(Array.from(middle?.children ?? []), 1)).toHaveTextContent(/^Sub \+\$120\.00$/);
    expect(total).toHaveTextContent(/^\+\$150$/);
    expect(total).toHaveClass('bg-money-up-fill');
  });

  it('is a white card at least 54px tall, its middle free to shrink so a long name truncates', () => {
    const card = renderInList(<ListCard position={1} name="Aces" nameCase="team" detail="detail" total={null} />);

    expect(card).toHaveClass('flex', 'min-h-13.5', 'rounded', 'bg-surface-card');
    expect(within(card).getByText('Aces').parentElement).toHaveClass('flex-1', 'min-w-0');
    expect(within(card).getByText('Aces')).toHaveClass('truncate');
  });

  it.each([
    ['team', 'uppercase', 'normal-case'],
    ['golfer', 'normal-case', 'uppercase'],
  ] as const)('sets a %s name in the condensed bold ink, cased by what it names', (nameCase, caseClass, otherCase) => {
    const card = renderInList(<ListCard position={3} name="Name" nameCase={nameCase} detail="detail" total={null} />);

    const name = within(card).getByText('Name');
    expect(name).toHaveClass('font-display', 'font-bold', 'text-lg', 'text-ink', caseClass);
    expect(name).not.toHaveClass(otherCase);
  });

  it('sets the detail line small, in the muted ink', () => {
    const card = renderInList(<ListCard position={3} name="Aces" nameCase="team" detail="the detail" total={null} />);

    expect(within(card).getByText('the detail')).toHaveClass('text-sm', 'text-ink-muted');
  });

  it('stacks the cards 4px apart', () => {
    renderInList(<ListCard position={1} name="Aces" nameCase="team" detail="detail" total={null} />);

    expect(screen.getByRole('list')).toHaveClass('flex', 'flex-col', 'gap-1');
  });

  it('keeps the plain card and its middle unchanged for the standings, scoreboard and players cards', () => {
    const card = renderInList(<ListCard position={2} name="Aces" nameCase="team" detail="detail" total={null} />);

    expect(card.className).toBe('flex min-h-13.5 rounded bg-surface-card');
    expect(within(card).getByText('Aces').parentElement?.className).toBe(
      'flex-1 min-w-0 px-3 py-2 flex flex-col justify-center',
    );
  });

  it('fills a late-row leader pale gold, with the gold edge on the middle beside the box', () => {
    const card = renderInList(
      <ListCard position={6} name="Aces" nameCase="golfer" detail="detail" total={null} fill="late-row-leader" />,
    );

    expect(card).toHaveClass('bg-late-row-leader');
    expect(card).not.toHaveClass('bg-surface-card');
    expect(within(card).getByText('Aces').parentElement).toHaveClass('border-l-4', 'border-gold');
  });

  it('numbers a round box 1 in navy, where a position 1 is gold', () => {
    const card = renderInList(
      <ListCard position={1} positionKind="round" name="Aces" nameCase="golfer" detail="detail" total={null} />,
    );

    const box = nth(Array.from(card.children), 0);
    expect(box).toHaveClass('bg-position-fill');
    expect(box).not.toHaveClass('bg-gold');
  });
});
