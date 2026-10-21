import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import type { api } from '@/shared/api/client';
import PickerRow from '@/shared/components/PickerRow';
import { TAP_TARGET_CLASS } from '@/shared/components/formControls';
import { makeLeague, makeSeason, makeTournament } from '@/shared/test/fixtures';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PhonePicker from './PhonePicker';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
  },
  ApiError: class ApiError extends Error {},
}));

const bermuda = makeTournament({ id: 'tn-11', name: 'Butterfield Bermuda', week: '11' });

const SUMMARY_NAME = 'Wk 11 · Butterfield Bermuda';
const HEADER_NAME = 'Wk 11, change league, season and tournament';

function renderTournamentField(idSuffix: string): ReactNode {
  return (
    <PickerRow
      id={`tournament-select${idSuffix}`}
      label="Tournament"
      value={bermuda.id}
      options={[{ value: bermuda.id, label: SUMMARY_NAME }]}
      onChange={() => undefined}
    />
  );
}

const mountedSlots: HTMLElement[] = [];

/** On the body, outside the render's container, as the nav's slot is outside the page. */
function mountSlot(): HTMLElement {
  const slot = document.createElement('div');
  document.body.append(slot);
  mountedSlots.push(slot);
  return slot;
}

describe('PhonePicker', () => {
  beforeEach(() => {
    leaguesMock.mockResolvedValue([makeLeague({ id: 'lg-1', name: 'Castlewood' })]);
    seasonsMock.mockResolvedValue([makeSeason({ id: 'sn-1', leagueId: 'lg-1', name: 'Spring', seasonYear: 2026 })]);
  });

  afterEach(() => {
    mountedSlots.splice(0).forEach((slot) => slot.remove());
    leaguesMock.mockReset();
    seasonsMock.mockReset();
  });

  it('opens one sheet from either trigger, and its close button closes it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PhonePicker tournament={bermuda} renderField={renderTournamentField} />, {
      headerSlot: mountSlot(),
    });

    await user.click(await screen.findByRole('button', { name: SUMMARY_NAME }));
    const sheet = screen.getByRole('dialog', { name: 'League, season and tournament' });
    expect(screen.getByRole('button', { name: SUMMARY_NAME })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: HEADER_NAME })).toHaveAttribute('aria-expanded', 'true');
    await user.click(within(sheet).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).toBeNull();

    await user.click(screen.getByRole('button', { name: HEADER_NAME }));
    expect(screen.getByRole('dialog', { name: 'League, season and tournament' })).toBeInTheDocument();
  });

  it('names the sheet from both triggers, which say they open a dialog', async () => {
    renderWithProviders(<PhonePicker tournament={bermuda} renderField={renderTournamentField} />, {
      headerSlot: mountSlot(),
    });

    const summary = await screen.findByRole('button', { name: SUMMARY_NAME });
    const header = screen.getByRole('button', { name: HEADER_NAME });
    for (const trigger of [summary, header]) {
      expect(trigger).toHaveAttribute('aria-haspopup', 'dialog');
      expect(trigger).toHaveAttribute('aria-expanded', 'false');
    }
    expect(header.getAttribute('aria-controls')).toBe(summary.getAttribute('aria-controls'));
  });

  it('points both triggers at the sheet content once it opens', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PhonePicker />, { headerSlot: mountSlot() });

    const summary = await screen.findByRole('button', { name: '2026 Spring' });
    await user.click(summary);

    const controlled = document.getElementById(summary.getAttribute('aria-controls') ?? '');
    expect(screen.getByRole('dialog', { name: 'League and season' })).toContainElement(controlled);
  });

  it('keeps the sheet open on a change, so a second pick needs no reopening', async () => {
    const user = userEvent.setup();
    leaguesMock.mockResolvedValue([
      makeLeague({ id: 'lg-1', name: 'Castlewood' }),
      makeLeague({ id: 'lg-2', name: 'Shadow' }),
    ]);
    renderWithProviders(<PhonePicker />);

    await user.click(await screen.findByRole('button', { name: '2026 Spring' }));
    const sheet = screen.getByRole('dialog', { name: 'League and season' });
    await user.selectOptions(within(sheet).getByRole('combobox', { name: 'League' }), 'Shadow');

    expect(screen.getByRole('dialog', { name: 'League and season' })).toBeInTheDocument();
  });

  it('reads the summary as the week and the tournament, its arrow hidden from the name', async () => {
    renderWithProviders(<PhonePicker tournament={bermuda} />);

    expect(await screen.findByRole('button', { name: SUMMARY_NAME })).toHaveTextContent(`${SUMMARY_NAME}▾`);
  });

  it("falls back to the selected season's label without a tournament", async () => {
    renderWithProviders(<PhonePicker />, { headerSlot: mountSlot() });

    expect(await screen.findByRole('button', { name: '2026 Spring' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2026 Spring, change league and season' })).toBeInTheDocument();
  });

  it('reads Choose season while no season is selected', async () => {
    seasonsMock.mockResolvedValue([]);
    renderWithProviders(<PhonePicker />);

    expect(await screen.findByRole('button', { name: 'Choose season' })).toBeInTheDocument();
  });

  it('reads a neutral Season while the seasons load', async () => {
    seasonsMock.mockReturnValue(new Promise(() => undefined));
    renderWithProviders(<PhonePicker />);

    expect(await screen.findByRole('button', { name: 'Season' })).toBeInTheDocument();
  });

  it('renders no trigger in either place while there is no league to pick', async () => {
    leaguesMock.mockResolvedValue([]);
    const slot = mountSlot();
    renderWithProviders(<PhonePicker tournament={bermuda} />, { headerSlot: slot });

    await waitFor(() => {
      expect(leaguesMock).toHaveBeenCalled();
    });
    expect(screen.queryByRole('button')).toBeNull();
    expect(slot).toBeEmptyDOMElement();
  });

  it('renders no header button while the slot is null', async () => {
    renderWithProviders(<PhonePicker tournament={bermuda} />);

    const summary = await screen.findByRole('button', { name: SUMMARY_NAME });
    expect(screen.getAllByRole('button')).toEqual([summary]);
  });

  it('renders the header button inside the slot, the week alone in the nav type', async () => {
    const slot = mountSlot();
    renderWithProviders(<PhonePicker tournament={bermuda} />, { headerSlot: slot });

    const header = await within(slot).findByRole('button', { name: 'Wk 11, change league and season' });
    expect(header).toHaveTextContent('Wk 11▾');
    expect(header).toHaveClass(TAP_TARGET_CLASS, 'font-display', 'uppercase', 'text-on-chrome');
  });

  it('hides the summary from sm, at full width below it', async () => {
    renderWithProviders(<PhonePicker tournament={bermuda} />);

    expect(await screen.findByRole('button', { name: SUMMARY_NAME })).toHaveClass(
      'sm:hidden',
      'w-full',
      'bg-surface-card',
      'border-control-border',
      TAP_TARGET_CLASS,
    );
  });

  it("gives the sheet's field ids of its own beside the desktop copy", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <>
        {renderTournamentField('')}
        <PhonePicker tournament={bermuda} renderField={renderTournamentField} />
      </>,
    );

    await user.click(await screen.findByRole('button', { name: SUMMARY_NAME }));

    const sheetSelect = within(screen.getByRole('dialog')).getByRole('combobox', { name: 'Tournament' });
    const selects = screen.getAllByRole('combobox', { name: 'Tournament' });
    expect(selects).toHaveLength(2);
    expect(new Set(selects.map((select) => select.id)).size).toBe(2);
    expect(selects).toContain(sheetSelect);
  });

  it('holds League and Season in the sheet while the page has no field to add', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PhonePicker />);

    await user.click(await screen.findByRole('button', { name: '2026 Spring' }));

    const sheet = screen.getByRole('dialog', { name: 'League and season' });
    expect(within(sheet).getByRole('combobox', { name: 'League' })).toBeInTheDocument();
    expect(within(sheet).getByRole('combobox', { name: 'Season' })).toBeEnabled();
    expect(within(sheet).queryByRole('combobox', { name: 'Tournament' })).toBeNull();
  });
});
