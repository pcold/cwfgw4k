import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import App from './App';
import { renderWithProviders } from './test/renderWithProviders';

vi.mock('./api/client', () => ({
  api: {
    leagues: vi.fn().mockResolvedValue([]),
    seasons: vi.fn().mockResolvedValue([]),
    seasonReport: vi.fn().mockResolvedValue({
      tournament: {
        id: null,
        name: null,
        startDate: null,
        endDate: null,
        status: null,
        payoutMultiplier: 1,
        week: null,
      },
      teams: [],
      undraftedTopTens: [],
      sideBetDetail: [],
      standingsOrder: [],
      live: false,
    }),
  },
  ApiError: class ApiError extends Error {},
}));

describe('App', () => {
  it('renders the Weekly Report nav link', () => {
    renderWithProviders(<App />);
    expect(screen.getByRole('link', { name: /Weekly Report/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'CWFG' })).toBeInTheDocument();
  });
});
