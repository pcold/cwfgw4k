import { Component, type ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasCrashed: boolean;
}

const RELOAD_BUTTON_CLASS =
  'mt-6 bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded text-sm font-medium';

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
      <div className="min-h-screen bg-gray-900 text-gray-100 flex items-center justify-center px-4">
        <div role="alert" className="w-full max-w-md bg-gray-800 rounded-lg shadow-xl border border-gray-600 p-8 text-center">
          <h1 className="text-2xl font-bold">Something went wrong</h1>
          <p className="mt-2 text-sm text-gray-400">
            CWFG hit an unexpected error and could not finish loading this screen. Reloading usually clears it.
          </p>
          <button type="button" className={RELOAD_BUTTON_CLASS} onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      </div>
    );
  }
}
