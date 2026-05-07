import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import type { Tournament } from '@/shared/api/types';

/**
 * Decide whether the live overlay applies to the given tournament selection
 * and (when it does) the value to pass through to API calls.
 *
 * Live overlay only adds signal for tournaments in the season's current
 * week — i.e. the week containing the earliest non-completed tournament.
 * That's where ESPN's projected payouts matter. Past-completed events are
 * immutable, future events haven't started, and "All Tournaments" rollups
 * never benefit. Multi-tournament weeks (e.g. a regular tour event plus an
 * opposite-field event) are common enough that scoping to a single id
 * leaves the second tournament permanently non-live; same-week scoping
 * keeps both eligible while the week is in flight.
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
  const earliest = tournaments.find((t) => t.status !== 'completed') ?? null;
  const selected = tournaments.find((t) => t.id === tournamentId) ?? null;
  const eligible =
    selected !== null &&
    earliest !== null &&
    selected.status !== 'completed' &&
    (selected.id === earliest.id || sameWeekNumber(selected.week, earliest.week));
  return {
    eligible,
    effectiveLive: eligible && live,
    toggleLive: setLive,
    liveChecked: live,
  };
}

// Multi-tournament weeks get suffixed labels like "4a" / "4b" (see
// AdminService.assignWeeks). The leading digits are the shared week number
// — that's what determines whether two events are concurrently in flight.
function sameWeekNumber(a: string | null, b: string | null): boolean {
  const left = weekNumber(a);
  const right = weekNumber(b);
  return left !== null && left === right;
}

function weekNumber(week: string | null): string | null {
  if (week === null) return null;
  const match = week.match(/^\d+/);
  return match === null ? null : match[0];
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
