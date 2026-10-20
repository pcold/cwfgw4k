import { useId, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import MoreSheet, { SHEET_PATHS } from './MoreSheet';

const ROUTE_TABS: ReadonlyArray<{ readonly to: string; readonly label: string }> = [
  { to: '/scoreboard', label: 'Scores' },
  { to: '/rankings', label: 'Standings' },
  { to: '/report', label: 'Report' },
  { to: '/player-rankings', label: 'Players' },
];

// Over the report grids' sticky z-20 column and corner, under the modal scrim's z-50.
const BAR_CLASS =
  'lg:hidden fixed inset-x-0 bottom-0 z-40 bg-chrome ' +
  'pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] pr-[env(safe-area-inset-right)]';

const tabClass = (isActive: boolean): string =>
  `relative flex items-center justify-center w-full h-full ${TAP_TARGET_CLASS} font-display uppercase tracking-wider ` +
  `text-xs ${isActive ? 'font-bold text-on-chrome' : 'font-medium text-on-chrome-tab-idle'}`;

function TabLabel({ label, isActive }: { readonly label: string; readonly isActive: boolean }): ReactNode {
  return (
    <>
      {isActive ? (
        <span aria-hidden="true" className="absolute top-0 inset-x-0 mx-auto w-8 h-1 bg-primary" />
      ) : null}
      {label}
    </>
  );
}

/**
 * Phones' navigation, below `lg`. The sheet is open only on the location it was opened from, so any navigation,
 * a tap on the current page's row included, closes it without an effect.
 */
export default function TabBar(): ReactNode {
  const location = useLocation();
  const sheetId = useId();
  const [sheetOpenedAt, setSheetOpenedAt] = useState<string | null>(null);
  const sheetOpen = sheetOpenedAt === location.key;
  const moreActive = SHEET_PATHS.includes(location.pathname);

  return (
    <>
      <nav aria-label="Tabs" className={BAR_CLASS}>
        <ul className="grid grid-cols-5 h-14">
          {ROUTE_TABS.map((tab) => (
            <li key={tab.to}>
              <NavLink to={tab.to} className={({ isActive }) => tabClass(isActive)}>
                {({ isActive }) => <TabLabel label={tab.label} isActive={isActive} />}
              </NavLink>
            </li>
          ))}
          <li>
            <button
              type="button"
              className={tabClass(moreActive)}
              aria-current={moreActive ? 'page' : undefined}
              aria-expanded={sheetOpen}
              aria-controls={sheetId}
              onClick={() => setSheetOpenedAt(location.key)}
            >
              <TabLabel label="More" isActive={moreActive} />
            </button>
          </li>
        </ul>
      </nav>
      {sheetOpen ? <MoreSheet id={sheetId} onClose={() => setSheetOpenedAt(null)} /> : null}
    </>
  );
}
