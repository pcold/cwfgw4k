import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from './formFields';
import TeamStrip from './TeamStrip';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const TEAMS = [
  { teamId: 't-1', teamName: 'Aces' },
  { teamId: 't-2', teamName: 'Birdies' },
  { teamId: 't-3', teamName: 'Condors' },
];

function strip(): HTMLElement {
  return screen.getByRole('group', { name: 'Team' });
}

describe('TeamStrip', () => {
  it('presses the selected team alone, in the chrome fill', () => {
    renderWithProviders(<TeamStrip teams={TEAMS} selectedTeamId="t-2" onSelect={vi.fn()} />, NO_PROVIDERS);

    const pressed = within(strip())
      .getAllByRole('button')
      .map((chip) => [chip.textContent, chip.getAttribute('aria-pressed')]);
    expect(pressed).toEqual([
      ['Aces', 'false'],
      ['Birdies', 'true'],
      ['Condors', 'false'],
    ]);
    expect(within(strip()).getByRole('button', { name: 'Birdies' })).toHaveClass('bg-chrome', 'text-on-chrome');
    expect(within(strip()).getByRole('button', { name: 'Aces' })).toHaveClass('bg-surface-card', 'text-ink');
  });

  it('reports the tapped team', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<TeamStrip teams={TEAMS} selectedTeamId="t-1" onSelect={onSelect} />, NO_PROVIDERS);

    await user.click(within(strip()).getByRole('button', { name: 'Condors' }));

    expect(onSelect).toHaveBeenCalledWith('t-3');
  });

  it('scrolls sideways, its chips at tap-target height and never wrapping a name', () => {
    renderWithProviders(<TeamStrip teams={TEAMS} selectedTeamId="t-1" onSelect={vi.fn()} />, NO_PROVIDERS);

    expect(strip()).toHaveClass('flex', 'overflow-x-auto');
    for (const chip of within(strip()).getAllByRole('button')) {
      expect(chip).toHaveClass(TAP_TARGET_CLASS, 'shrink-0', 'whitespace-nowrap');
    }
  });

  it('fades the right edge over as much end padding, so the last chip scrolls clear of the fade', () => {
    renderWithProviders(<TeamStrip teams={TEAMS} selectedTeamId="t-1" onSelect={vi.fn()} />, NO_PROVIDERS);

    expect(strip()).toHaveClass('mask-r-from-[calc(100%-2rem)]', 'pr-8');
  });
});
