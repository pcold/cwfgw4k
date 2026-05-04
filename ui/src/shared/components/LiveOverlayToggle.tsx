import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { earliestUnfinalized } from '@/shared/util/tournament';
import type { Tournament } from '@/shared/api/types';

/**
 * Decide whether the live overlay applies to the given tournament selection
 * and (when it does) the value to pass through to API calls.
 *
 * Live overlay only adds signal for the season's earliest non-finalized
 * tournament — that's the one currently in play (or about to start) where
 * ESPN's projected payouts matter. Past-completed events are immutable, so
 * the overlay is wasted ESPN traffic. Future events haven't started, so
 * there's nothing live to show. "All Tournaments" rollups likewise never
 * benefit.
 *
 * Returns:
 *   eligible — show the toggle? false hides it everywhere.
 *   effectiveLive — pass to api calls. Always false when not eligible,
 *     even if the user previously checked the toggle on another view.
 */
export interface LiveOverlayState {
  eligible: boolean;
  effectiveLive: boolean;
  toggleLive: (next: boolean) => void;
  liveChecked: boolean;
}

export function useLiveOverlay(
  tournaments: Tournament[],
  tournamentId: string | null,
): LiveOverlayState {
  const { live, setLive } = useLeagueSeason();
  const earliestId = earliestUnfinalized(tournaments);
  const eligible = tournamentId !== null && earliestId !== null && tournamentId === earliestId;
  return {
    eligible,
    effectiveLive: eligible && live,
    toggleLive: setLive,
    liveChecked: live,
  };
}

interface LiveOverlayCheckboxProps {
  state: LiveOverlayState;
  id?: string;
}

export function LiveOverlayCheckbox({ state, id }: LiveOverlayCheckboxProps) {
  if (!state.eligible) return null;
  return (
    <label
      htmlFor={id}
      className="flex items-center gap-2 cursor-pointer text-xs text-gray-400"
    >
      <input
        id={id}
        type="checkbox"
        checked={state.liveChecked}
        onChange={(e) => state.toggleLive(e.target.checked)}
        className="accent-green-500 w-4 h-4"
      />
      <span>Live overlay</span>
    </label>
  );
}
