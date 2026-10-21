import type { ReactNode } from 'react';
import { NavLink } from 'react-router';
import ModalShell from '@/shared/components/ModalShell';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { useMyTeam } from '@/features/myTeam/MyTeamContext';
import type { MyTeamChoice } from '@/features/myTeam/myTeamModel';

const SHEET_PAGES: ReadonlyArray<{ readonly to: string; readonly label: string }> = [
  { to: '/rosters', label: 'Rosters' },
  { to: '/late-row-bets', label: 'Bets' },
  { to: '/rules', label: 'Rules' },
];

const ADMIN_PATH = '/admin';

/** The More tab reads as the current page on any of these. */
export const SHEET_PATHS: readonly string[] = [...SHEET_PAGES.map((page) => page.to), ADMIN_PATH];

const rowClass = ({ isActive }: { isActive: boolean }): string =>
  `flex items-center ${TAP_TARGET_CLASS} px-3 rounded text-base font-display font-semibold uppercase tracking-wider ` +
  (isActive ? 'bg-surface-raised text-ink-strong' : 'text-ink hover:bg-surface-raised');

function myTeamRowLabel(choice: MyTeamChoice): string {
  switch (choice.kind) {
    case 'team':
      return `My team: ${choice.teamName} · Change`;
    case 'browsing':
    case 'unasked':
      return 'My team · Choose';
    default:
      return choice satisfies never;
  }
}

interface MoreSheetProps {
  /** On the sheet's content, not the dialog, which takes no id; the More tab's `aria-controls` names it. */
  readonly id: string;
  readonly onClose: () => void;
}

/** Never closes itself on navigation: `TabBar` remounts it. @see ModalShell */
export default function MoreSheet({ id, onClose }: MoreSheetProps): ReactNode {
  const { choice, canAsk, changeTeam } = useMyTeam();
  return (
    <ModalShell title="More" onClose={onClose} placement="sheet" dismissOnBackdrop>
      <div id={id}>
        <ul className="space-y-1">
          {SHEET_PAGES.map((page) => (
            <li key={page.to}>
              <NavLink to={page.to} className={rowClass}>
                {page.label}
              </NavLink>
            </li>
          ))}
          {canAsk ? (
            <li>
              <button
                type="button"
                className={`w-full text-left ${rowClass({ isActive: false })}`}
                onClick={() => {
                  changeTeam();
                  onClose();
                }}
              >
                {myTeamRowLabel(choice)}
              </button>
            </li>
          ) : null}
        </ul>
        <hr className="my-3 border-hairline" />
        <NavLink to={ADMIN_PATH} className={rowClass}>
          Admin
        </NavLink>
      </div>
    </ModalShell>
  );
}
