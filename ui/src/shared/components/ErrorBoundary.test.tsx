import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ErrorBoundary from './ErrorBoundary';
import { TAP_TARGET_CLASS } from './formControls';

function Boom(): never {
  throw new Error('render exploded');
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    // React reports every error a boundary catches to console.error itself, so the throwing
    // tests below would otherwise print a component stack that reads like a real failure.
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders its children when nothing throws', () => {
    renderWithProviders(
      <ErrorBoundary>
        <p>Weekly report</p>
      </ErrorBoundary>,
      { withLeagueSeasonProvider: false },
    );

    expect(screen.getByText('Weekly report')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a recovery fallback instead of a blank page when a child throws while rendering', () => {
    renderWithProviders(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
      { withLeagueSeasonProvider: false },
    );

    expect(screen.getByRole('heading', { name: /something went wrong/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reload/i })).toBeInTheDocument();
  });

  it('keeps the failing markup out of the fallback', () => {
    renderWithProviders(
      <ErrorBoundary>
        <p>Weekly report</p>
        <Boom />
      </ErrorBoundary>,
      { withLeagueSeasonProvider: false },
    );

    expect(screen.queryByText('Weekly report')).not.toBeInTheDocument();
    expect(screen.queryByText(/render exploded/)).not.toBeInTheDocument();
  });

  it('reloads the page from the fallback', async () => {
    const reload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload });

    renderWithProviders(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
      { withLeagueSeasonProvider: false },
    );
    await userEvent.click(screen.getByRole('button', { name: /reload/i }));

    expect(reload).toHaveBeenCalledOnce();
  });

  it('links back to the scoreboard for a page that crashes again on reload', () => {
    renderWithProviders(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
      { withLeagueSeasonProvider: false },
    );

    expect(screen.getByRole('link', { name: /back to the scoreboard/i })).toHaveAttribute('href', '/');
  });

  it('gives the reload button and the scoreboard link the tap-target height', () => {
    renderWithProviders(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
      { withLeagueSeasonProvider: false },
    );

    expect(screen.getByRole('button', { name: /reload/i })).toHaveClass(TAP_TARGET_CLASS);
    expect(screen.getByRole('link', { name: /back to the scoreboard/i })).toHaveClass(
      TAP_TARGET_CLASS,
      'flex',
      'items-center',
    );
  });
});
