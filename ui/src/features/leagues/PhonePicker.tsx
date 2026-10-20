import { useId, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import type { Tournament } from '@/shared/api/types';
import { CONTROL_CLASS, TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { useHeaderSlot } from '@/shared/components/HeaderSlot';
import ModalShell from '@/shared/components/ModalShell';
import { seasonLabel } from '@/shared/util/season';
import { tournamentShortLabel, tournamentWeekLabel } from '@/shared/util/tournament';
import { useLeagueSeason } from './LeagueSeasonContext';
import LeagueSeasonPicker from './LeagueSeasonPicker';

/** Wraps a page's desktop copy of each picker the sheet holds below `sm:`. */
export const DESKTOP_PICKER_CLASS = 'hidden sm:block';

const SHEET_ID_SUFFIX = '-sheet';

const NO_SEASON_LABEL = 'Choose season';

const SUMMARY_CLASS =
  `sm:hidden flex items-center justify-between gap-2 w-full bg-surface-card ${CONTROL_CLASS} ${TAP_TARGET_CLASS} ` +
  'text-left font-display font-medium text-ink-strong';

const HEADER_CLASS =
  `flex items-center gap-1.5 min-w-0 ${TAP_TARGET_CLASS} px-3 text-sm font-display font-semibold uppercase ` +
  'tracking-wider text-on-chrome';

interface PhonePickerProps {
  /** Unset on a page without a tournament field, on All Tournaments, or before the list loads. */
  readonly tournament?: Tournament;
  /**
   * The page's tournament field again, for the sheet: it must suffix every id it renders with `idSuffix`,
   * since its desktop copy stays in the DOM. Unset until the page's tournaments load.
   */
  readonly renderField?: (idSuffix: string) => ReactNode;
}

/**
 * Below `sm:` only: a summary button under the page title and a week button in the nav, both opening one sheet
 * of the page's pickers. The page renders it outside its tournaments' loading gate, so League and Season stay
 * reachable while the tournaments load or fail.
 */
export default function PhonePicker({ tournament, renderField }: PhonePickerProps): ReactNode {
  const { leagues, seasons, seasonId } = useLeagueSeason();
  const headerSlot = useHeaderSlot();
  const sheetId = useId();
  const [open, setOpen] = useState(false);

  // LeagueSeasonPicker's own guard: without a league the sheet would hold nothing to pick.
  if (!leagues || leagues.length === 0) return null;

  const season = seasons?.find((s) => s.id === seasonId);
  const fallbackLabel = season ? seasonLabel(season) : NO_SEASON_LABEL;
  const summaryLabel = tournament ? tournamentShortLabel(tournament) : fallbackLabel;
  const headerLabel = tournament ? tournamentWeekLabel(tournament) : fallbackLabel;
  const sheetTitle = renderField ? 'League, season and tournament' : 'League and season';

  const headerButton = (
    <button
      type="button"
      className={HEADER_CLASS}
      aria-label={`${headerLabel}, change ${sheetTitle.toLowerCase()}`}
      aria-haspopup="dialog"
      aria-expanded={open}
      aria-controls={sheetId}
      onClick={() => setOpen(true)}
    >
      <span className="truncate">{headerLabel}</span>
      <span aria-hidden="true">▾</span>
    </button>
  );

  return (
    <>
      <button
        type="button"
        className={SUMMARY_CLASS}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={sheetId}
        onClick={() => setOpen(true)}
      >
        <span className="truncate">{summaryLabel}</span>
        <span aria-hidden="true">▾</span>
      </button>
      {headerSlot ? createPortal(headerButton, headerSlot) : null}
      {open ? (
        <ModalShell title={sheetTitle} onClose={() => setOpen(false)} placement="sheet" dismissOnBackdrop>
          <div id={sheetId} className="space-y-4">
            <LeagueSeasonPicker />
            {renderField?.(SHEET_ID_SUFFIX)}
          </div>
        </ModalShell>
      ) : null}
    </>
  );
}
