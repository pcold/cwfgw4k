import type { ReactNode } from 'react';
import { skipToken, useQuery, type QueryKey, type UseQueryResult } from '@tanstack/react-query';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { TAP_TARGET_CLASS } from './formControls';

/** @see useLiveOverlayQuery */
export interface LiveOverlayState {
  eligible: boolean;
  liveChecked: boolean;
  toggleLive: (next: boolean) => void;
}

/**
 * The server's `espn.scoreboardCacheTtlSeconds`: it holds one ESPN scoreboard that long, so a faster poll re-reads
 * the same one. A change to that setting changes this.
 */
export const LIVE_REFRESH_INTERVAL_MS = 60_000;

interface LiveEligibleResponse {
  liveEligible: boolean;
}

/** @see useLiveOverlayQuery */
export interface LiveOverlayQuery<T> {
  query: UseQueryResult<T>;
  overlay: LiveOverlayState;
}

/**
 * The non-live response's `liveEligible` is the only rule: the live request waits for it, so a selection the
 * server would not overlay never asks for one. Pass a null `fetchSelection` while the selection is unresolved.
 * The overlay refetches every `LIVE_REFRESH_INTERVAL_MS` while the tab is visible, and on return to a tab whose
 * data is older than that.
 */
export function useLiveOverlayQuery<T extends LiveEligibleResponse>(
  queryKey: (live: boolean) => QueryKey,
  fetchSelection: ((live: boolean) => Promise<T>) | null,
): LiveOverlayQuery<T> {
  const { live, setLive } = useLeagueSeason();
  const base = useQuery({
    queryKey: queryKey(false),
    queryFn: fetchSelection === null ? skipToken : () => fetchSelection(false),
  });
  const eligible = base.data?.liveEligible ?? false;
  const effectiveLive = eligible && live;
  const overlaid = useQuery({
    queryKey: queryKey(true),
    queryFn: fetchSelection === null || !effectiveLive ? skipToken : () => fetchSelection(true),
    staleTime: LIVE_REFRESH_INTERVAL_MS,
    refetchInterval: LIVE_REFRESH_INTERVAL_MS,
    refetchOnWindowFocus: true,
  });
  return {
    query: effectiveLive ? overlaid : base,
    overlay: { eligible, liveChecked: live, toggleLive: setLive },
  };
}

interface LiveOverlayCheckboxProps {
  state: LiveOverlayState;
  id?: string;
}

export function LiveOverlayCheckbox({ state, id }: LiveOverlayCheckboxProps): ReactNode {
  if (!state.eligible) return null;
  return (
    <label
      htmlFor={id}
      className={`flex items-center gap-2 ${TAP_TARGET_CLASS} cursor-pointer text-xs text-ink-muted`}
    >
      <input
        id={id}
        type="checkbox"
        checked={state.liveChecked}
        onChange={(e) => state.toggleLive(e.target.checked)}
        className="accent-primary-control w-4 h-4"
      />
      <span>Live overlay</span>
    </label>
  );
}
