import type { ReactElement } from 'react';

// Its own leading margin, since it always follows a team's name. `shrink-0` keeps it whole beside a name that
// truncates.
const TAG_CLASS =
  'ml-2 inline-flex shrink-0 items-center align-middle whitespace-nowrap rounded px-1.5 py-0.5 font-display ' +
  'font-bold text-xs uppercase tracking-wider bg-chrome text-on-chrome';

/**
 * A screen reader hears "your team" in place of the bare label. No `className`, so a caller can't add a second
 * fill.
 */
export default function YouTag(): ReactElement {
  return (
    <span className={TAG_CLASS}>
      <span aria-hidden="true">You</span>
      <span className="sr-only">your team</span>
    </span>
  );
}
