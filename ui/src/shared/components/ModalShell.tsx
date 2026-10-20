import { useEffect, useId, useRef, useState, type ReactElement, type ReactNode, type RefObject } from 'react';
import { TAP_TARGET_CLASS } from './formFields';

/** Unconditional: a modal covers the viewport at every width. */
export const MODAL_SCROLL_LOCK_CLASS = 'overflow-hidden';

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

/** `wide` is for a list of editable rows, whose controls sit beside each row's label. */
export type ModalWidth = 'form' | 'detail' | 'wide';

const PANEL_GEOMETRY = 'bg-surface-card rounded-lg shadow-xl max-h-[90dvh] overflow-y-auto border border-hairline-strong';

const PANEL_WIDTH: Record<ModalWidth, string> = {
  form: 'w-[min(92vw,22rem)]',
  detail: 'w-[min(92vw,28rem)]',
  wide: 'w-full max-w-3xl',
};

const CENTRED_OVERLAY = 'fixed inset-0 bg-overlay/60 flex items-center justify-center z-50 p-3 sm:p-4';

const SHEET_OVERLAY = 'fixed inset-0 bg-overlay/60 flex items-end z-50';

// No shadow: the scrim is all that lies above the sheet's one edge.
const SHEET_PANEL =
  'bg-surface-card rounded-t-lg max-h-[90dvh] overflow-y-auto border-t border-hairline-strong w-full ' +
  'pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] pr-[env(safe-area-inset-right)] ' +
  'transition-transform duration-200 ease-out starting:translate-y-full motion-reduce:transition-none';

/** A sheet spans the viewport, so it takes no width. */
type ModalPlacement =
  | { readonly placement?: 'centred'; readonly width?: ModalWidth }
  | { readonly placement: 'sheet'; readonly width?: never };

type ModalShellProps = ModalPlacement & {
  /** Also supplies the dialog's accessible name. */
  readonly title: string;
  readonly onClose: () => void;
  /** Off by default: a click that misses a form's panel would discard what was typed into it. */
  readonly dismissOnBackdrop?: boolean;
  readonly children: ReactNode;
};

/** Null leaves the keypress to the browser, whose own tab order already stays inside. */
function trapDestination(panel: HTMLElement, event: KeyboardEvent): HTMLElement | null {
  const focusable = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (!first || !last) {
    return panel;
  }
  const focused = document.activeElement;
  if (!panel.contains(focused)) {
    return event.shiftKey ? last : first;
  }
  if (event.shiftKey) {
    // The panel precedes its own content in the tab order, so backwards from it also leaves.
    return focused === first || focused === panel ? last : null;
  }
  return focused === last ? first : null;
}

/**
 * Trapped, not merely moved: `aria-modal` has told a screen reader the page is inert, so the keyboard
 * must not reach it either. Mount is open. Shells don't stack: each owns the scroll lock and Escape
 * outright, so a second one open at once would be released and closed with the first.
 */
function useModalDialog(onClose: () => void): RefObject<HTMLDivElement | null> {
  const panelRef = useRef<HTMLDivElement>(null);
  // Read during render: a child's `autoFocus` has already moved focus by the time an effect runs.
  const [opener] = useState(() => document.activeElement);

  useEffect(() => {
    function handleKey(event: KeyboardEvent): void {
      const panel = panelRef.current;
      if (!panel) {
        return;
      }
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }
      const destination = trapDestination(panel, event);
      if (destination) {
        event.preventDefault();
        destination.focus();
      }
    }
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('keydown', handleKey);
    };
  }, [onClose]);

  useEffect(() => {
    document.body.classList.add(MODAL_SCROLL_LOCK_CLASS);
    return () => {
      document.body.classList.remove(MODAL_SCROLL_LOCK_CLASS);
    };
  }, []);

  useEffect(() => {
    const panel = panelRef.current;
    if (panel && !panel.contains(document.activeElement)) {
      panel.focus();
    }
    return () => {
      // A trigger the modal's own mutation removed is gone by the time focus comes back; the caller
      // re-renders around that, so leave focus where it is.
      if (opener instanceof HTMLElement && opener.isConnected) {
        opener.focus();
      }
    };
  }, [opener]);

  return panelRef;
}

/**
 * For a modal gating its body on a query, whose `QueryState` must sit inside the panel, and for the phone's More
 * sheet; all else uses `FormModal`. The cap is `dvh`: mobile `vh` ignores the on-screen keyboard and pushes the footer off-screen.
 */
export default function ModalShell({
  title,
  onClose,
  width = 'form',
  placement = 'centred',
  dismissOnBackdrop = false,
  children,
}: ModalShellProps): ReactElement {
  const titleId = useId();
  const panelRef = useModalDialog(onClose);
  return (
    /* eslint-disable jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions --
       a backdrop click is a mouse shortcut; Escape and the Close button are the keyboard paths. */
    <div
      className={placement === 'sheet' ? SHEET_OVERLAY : CENTRED_OVERLAY}
      onClick={(event) => {
        if (dismissOnBackdrop && event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      {/* eslint-enable jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={placement === 'sheet' ? SHEET_PANEL : `${PANEL_GEOMETRY} ${PANEL_WIDTH[width]}`}
      >
        <div className="px-5 py-4 border-b border-hairline flex items-center justify-between">
          <h2 id={titleId} className="text-lg font-bold">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className={`text-ink-muted hover:text-ink-strong text-xl leading-none px-2 ${TAP_TARGET_CLASS}`}
          >
            ×
          </button>
        </div>
        <div className="px-5 py-4">{children}</div>
      </div>
    </div>
  );
}
