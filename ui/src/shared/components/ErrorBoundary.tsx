import { Component, type ReactNode } from 'react';
import { TAP_TARGET_CLASS } from './formFields';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasCrashed: boolean;
}

const RELOAD_BUTTON_CLASS =
  `mt-6 bg-primary hover:bg-primary-hover text-on-primary px-4 py-2 rounded text-sm font-medium ${TAP_TARGET_CLASS}`;

/**
 * Render throws only; `QueryState` owns fetch failures. A class because error catching has no hook.
 * No `componentDidCatch` until there is a beacon to report to: React already logs what a boundary catches.
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { hasCrashed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasCrashed: true };
  }

  override render(): ReactNode {
    if (!this.state.hasCrashed) {
      return this.props.children;
    }
    return (
      <div className="min-h-screen bg-canvas text-ink flex items-center justify-center px-4">
        <div role="alert" className="w-full max-w-md bg-surface-card rounded-lg shadow-xl border border-hairline-strong p-8 text-center">
          <h1 className="text-2xl font-bold">Something went wrong</h1>
          <p className="mt-2 text-sm text-ink-muted">
            CWFG hit an unexpected error and could not finish loading this screen. Reloading usually clears it.
          </p>
          <button type="button" className={RELOAD_BUTTON_CLASS} onClick={() => window.location.reload()}>
            Reload
          </button>
          {/* A plain anchor, not a router Link: the boundary sits outside the router. The way out when this URL
              crashes on every load. */}
          <a
            href="/"
            className={`mt-4 flex items-center justify-center ${TAP_TARGET_CLASS} text-sm text-ink-muted hover:text-ink-strong`}
          >
            Back to the scoreboard
          </a>
        </div>
      </div>
    );
  }
}
