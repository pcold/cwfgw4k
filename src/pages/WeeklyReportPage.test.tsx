import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import WeeklyReportPage from './WeeklyReportPage';
import type { League, Season, WeeklyReport } from '../api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const reportMock = vi.fn();

vi.mock('../api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: (id: string, live: boolean) => reportMock(id, live),
    rankings: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

const league: League = { id: 'lg-1', name: 'Test League', createdAt: '2026-01-01T00:00:00Z' };
const season: Season = {
  id: 'sn-1',
  leagueId: 'lg-1',
  name: 'Season 1',
  seasonYear: 2026,
  seasonNumber: 1,
  status: 'active',
};
const report: WeeklyReport = {
  tournament: {
    id: 't-1',
    name: 'Sample Open',
    startDate: '2026-03-01',
    endDate: '2026-03-04',
    status: 'completed',
    payoutMultiplier: 1,
    week: '9',
  },
  teams: [
    {
      teamId: 'team-1',
      teamName: 'Aces',
      ownerName: 'Alice',
      rows: [],
      topTens: 0,
      weeklyTotal: 25,
      previous: 100,
      subtotal: 125,
      topTenCount: 1,
      topTenMoney: 18,
      sideBets: 0,
      totalCash: 125,
    },
  ],
  undraftedTopTens: [],
  sideBetDetail: [],
  standingsOrder: [{ rank: 1, teamName: 'Aces', totalCash: 125 }],
  live: false,
};

describe('WeeklyReportPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    reportMock.mockReset();
  });

  it('renders the report when leagues → seasons → report all succeed', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    reportMock.mockResolvedValue(report);

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
    expect(seasonsMock).toHaveBeenCalledWith('lg-1');
    expect(reportMock).toHaveBeenCalledWith('sn-1', false);
  });

  it('shows an empty state when there are no leagues', async () => {
    leaguesMock.mockResolvedValue([]);
    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
  });

  it('surfaces an error when the leagues query fails', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });

  it('surfaces an error when the report query fails', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    reportMock.mockRejectedValue(new Error('kaboom'));

    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/Failed to load report/i)).toBeInTheDocument();
  });
});
