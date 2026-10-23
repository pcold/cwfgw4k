import { Component, type ReactNode } from 'react';
import Button from './Button';
import { TAP_TARGET_CLASS } from './formControls';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasCrashed: boolean;
}

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
        <div
          role="alert"
          className="w-full max-w-md bg-surface-card rounded-lg shadow-xl border border-hairline-strong p-8 text-center"
        >
          <h1 className="text-2xl font-bold">Something went wrong</h1>
          <p className="mt-2 text-sm text-ink-muted">
            CWFG hit an unexpected error and could not finish loading this screen. Reloading usually clears it.
          </p>
          <Button tone="primary" size="regular" layout="mt-6" onClick={() => window.location.reload()}>
            Reload
          </Button>
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
