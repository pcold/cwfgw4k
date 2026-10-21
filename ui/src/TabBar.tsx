import { useId, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router';
import { TAP_TARGET_CLASS } from '@/shared/components/formControls';
import MoreSheet, { SHEET_PATHS } from './MoreSheet';

const ROUTE_TABS: ReadonlyArray<{ readonly to: string; readonly label: string }> = [
  { to: '/scoreboard', label: 'Scores' },
  { to: '/rankings', label: 'Standings' },
  { to: '/report', label: 'Report' },
  { to: '/player-rankings', label: 'Players' },
];

// Over the report grids' sticky column (z-10) and corner (z-20), under the modal scrim's z-50.
const BAR_CLASS =
  'lg:hidden fixed inset-x-0 bottom-0 z-40 bg-chrome ' +
  'pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] pr-[env(safe-area-inset-right)]';

const tabClass = (isActive: boolean): string =>
  `relative flex items-center justify-center w-full h-full ${TAP_TARGET_CLASS} font-display uppercase tracking-wider ` +
  `text-xs ${isActive ? 'font-bold text-on-chrome' : 'font-medium text-on-chrome-tab-idle'}`;

interface TabLabelProps {
  readonly label: string;
  readonly isActive: boolean;
}

function TabLabel({ label, isActive }: TabLabelProps): ReactNode {
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
 * Keyed by the location, so any navigation remounts the bar with its sheet closed: a tap on the current page's row,
 * and a return to the history entry the sheet was opened on, which brings that entry's key back.
 */
export default function TabBar(): ReactNode {
  const location = useLocation();
  return <TabBarAt key={location.key} pathname={location.pathname} />;
}

interface TabBarAtProps {
  readonly pathname: string;
}

function TabBarAt({ pathname }: TabBarAtProps): ReactNode {
  const sheetId = useId();
  const [sheetOpen, setSheetOpen] = useState(false);
  const moreActive = SHEET_PATHS.includes(pathname);

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
              onClick={() => setSheetOpen(true)}
            >
              <TabLabel label="More" isActive={moreActive} />
            </button>
          </li>
        </ul>
      </nav>
      {sheetOpen ? <MoreSheet id={sheetId} onClose={() => setSheetOpen(false)} /> : null}
    </>
  );
}
