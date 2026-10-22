import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { focusManager } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import type { api } from '@/shared/api/client';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from './formControls';
import { LIVE_REFRESH_INTERVAL_MS, LiveOverlayCheckbox, useLiveOverlayQuery } from './LiveOverlayToggle';

const leaguesMock = vi.fn<typeof api.leagues>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('LiveOverlayCheckbox', () => {
  it('reports a tap anywhere on its label as the toggle', async () => {
    const toggleLive = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <LiveOverlayCheckbox id="live" state={{ eligible: true, liveChecked: false, toggleLive }} />,
      NO_PROVIDERS,
    );

    await user.click(screen.getByText('Live overlay'));

    expect(toggleLive).toHaveBeenCalledWith(true);
  });

  // The 16px box is too small to hit; the label around it is the tap target.
  it('gives the label around the checkbox the tap-target height', () => {
    renderWithProviders(
      <LiveOverlayCheckbox id="live" state={{ eligible: true, liveChecked: false, toggleLive: vi.fn() }} />,
      NO_PROVIDERS,
    );

    expect(screen.getByRole('checkbox', { name: 'Live overlay' }).closest('label')).toHaveClass(TAP_TARGET_CLASS);
  });
});

interface SelectionReport {
  liveEligible: boolean;
}

const fetchSelection = vi.fn<(live: boolean) => Promise<SelectionReport>>();

function Probe(): ReactNode {
  const [week, setWeek] = useState(1);
  const { overlay, selectionKey } = useLiveOverlayQuery((live) => ['probe', week, live], fetchSelection);
  return (
    <>
      <LiveOverlayCheckbox id="live" state={overlay} />
      <button type="button" onClick={() => setWeek(week + 1)}>
        Next week
      </button>
      <output aria-label="Selection key">{selectionKey}</output>
    </>
  );
}

const shownSelectionKey = (): string => screen.getByRole('status', { name: 'Selection key' }).textContent;

const liveFetches = (): number => fetchSelection.mock.calls.filter(([live]) => live).length;

describe('useLiveOverlayQuery', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    leaguesMock.mockResolvedValue([]);
    fetchSelection.mockReset();
  });

  afterEach(() => {
    focusManager.setFocused(undefined);
    vi.useRealTimers();
  });

  it('refetches the live overlay every refresh interval while the tab is visible', async () => {
    fetchSelection.mockResolvedValue({ liveEligible: true });
    renderWithProviders(<Probe />);
    await waitFor(() => expect(liveFetches()).toBe(1));

    await vi.advanceTimersByTimeAsync(LIVE_REFRESH_INTERVAL_MS);
    expect(liveFetches()).toBe(2);
    await vi.advanceTimersByTimeAsync(LIVE_REFRESH_INTERVAL_MS);
    expect(liveFetches()).toBe(3);
  });

  it('holds still while the tab is hidden, and catches up when it returns', async () => {
    fetchSelection.mockResolvedValue({ liveEligible: true });
    renderWithProviders(<Probe />);
    await waitFor(() => expect(liveFetches()).toBe(1));

    focusManager.setFocused(false);
    await vi.advanceTimersByTimeAsync(LIVE_REFRESH_INTERVAL_MS * 3);
    expect(liveFetches()).toBe(1);

    focusManager.setFocused(true);
    await waitFor(() => expect(liveFetches()).toBe(2));
  });

  it('never polls a week the server would not overlay', async () => {
    fetchSelection.mockResolvedValue({ liveEligible: false });
    renderWithProviders(<Probe />);
    await waitFor(() => expect(fetchSelection).toHaveBeenCalledTimes(1));

    await vi.advanceTimersByTimeAsync(LIVE_REFRESH_INTERVAL_MS * 3);
    expect(fetchSelection).toHaveBeenCalledTimes(1);
  });

  it('stops polling once the overlay is switched off', async () => {
    fetchSelection.mockResolvedValue({ liveEligible: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithProviders(<Probe />);
    await waitFor(() => expect(liveFetches()).toBe(1));

    await user.click(screen.getByRole('checkbox', { name: 'Live overlay' }));
    await vi.advanceTimersByTimeAsync(LIVE_REFRESH_INTERVAL_MS * 3);
    expect(liveFetches()).toBe(1);
  });

  describe('selectionKey', () => {
    async function renderLive(): Promise<string> {
      fetchSelection.mockResolvedValue({ liveEligible: true });
      renderWithProviders(<Probe />);
      await waitFor(() => expect(liveFetches()).toBe(1));
      return shownSelectionKey();
    }

    it('keeps its key through a refetch', async () => {
      const before = await renderLive();

      await vi.advanceTimersByTimeAsync(LIVE_REFRESH_INTERVAL_MS);

      expect(liveFetches()).toBe(2);
      expect(shownSelectionKey()).toBe(before);
    });

    it('takes a new key when the overlay is switched off', async () => {
      const before = await renderLive();
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      await user.click(screen.getByRole('checkbox', { name: 'Live overlay' }));

      expect(shownSelectionKey()).not.toBe(before);
    });

    it('takes a new key for a new selection', async () => {
      const before = await renderLive();
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      await user.click(screen.getByRole('button', { name: 'Next week' }));

      expect(shownSelectionKey()).not.toBe(before);
    });
  });
});
