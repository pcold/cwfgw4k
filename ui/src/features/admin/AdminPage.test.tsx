import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminPage from './AdminPage';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { ApiError } from '@/shared/api/client';

const authMeMock = vi.fn();
const loginMock = vi.fn();
const logoutMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    authMe: () => authMeMock(),
    login: (username: string, password: string) => loginMock(username, password),
    logout: () => logoutMock(),
    leagues: vi.fn().mockResolvedValue([]),
    seasons: vi.fn().mockResolvedValue([]),
    createLeague: vi.fn().mockResolvedValue({ id: 'lg-1', name: 'League 1' }),
    createSeason: vi.fn().mockResolvedValue({ id: 'sn-1' }),
    uploadSchedule: vi.fn().mockResolvedValue({
      seasonYear: 2026,
      tournamentsCreated: 0,
      espnMatched: 0,
      espnUnmatched: [],
      tournaments: [],
    }),
    previewRoster: vi.fn().mockResolvedValue({
      totalPicks: 0,
      matchedCount: 0,
      ambiguousCount: 0,
      unmatchedCount: 0,
      teams: [],
    }),
    confirmRoster: vi.fn().mockResolvedValue({
      teamsCreated: 0,
      golfersCreated: 0,
      teams: [],
    }),
    tournaments: vi.fn().mockResolvedValue([]),
    resetTournament: vi.fn().mockResolvedValue({ message: 'ok' }),
    cleanSeasonResults: vi.fn().mockResolvedValue({ message: 'ok' }),
  },
  ApiError: class ApiError extends Error {
    readonly status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

describe('AdminPage', () => {
  beforeEach(() => {
    authMeMock.mockReset();
    loginMock.mockReset();
    logoutMock.mockReset();
  });

  const adminUser = {
    id: 'u-1',
    username: 'admin',
    role: 'admin' as const,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('shows the login modal when the user is not authenticated', async () => {
    authMeMock.mockResolvedValue(null);

    renderWithProviders(<AdminPage />, {
      withAuthProvider: true,
      withLeagueSeasonProvider: false,
    });

    expect(await screen.findByRole('dialog', { name: /Admin Login/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Password/i)).toBeInTheDocument();
  });

  it('logs in successfully and reveals the admin page', async () => {
    authMeMock.mockResolvedValue(null);
    loginMock.mockResolvedValue(adminUser);

    const user = userEvent.setup();
    renderWithProviders(<AdminPage />, {
      withAuthProvider: true,
      withLeagueSeasonProvider: false,
    });

    await user.type(await screen.findByLabelText(/Username/i), 'admin');
    await user.type(screen.getByLabelText(/Password/i), 'hunter2');
    await user.click(screen.getByRole('button', { name: /Log In/i }));

    expect(loginMock).toHaveBeenCalledWith('admin', 'hunter2');
    expect(await screen.findByRole('heading', { name: 'Admin' })).toBeInTheDocument();
    expect(screen.getByText(/Signed in as admin/i)).toBeInTheDocument();
  });

  it('surfaces an invalid-credentials error on 401', async () => {
    authMeMock.mockResolvedValue(null);
    loginMock.mockRejectedValue(new ApiError(401, 'invalid username or password'));

    const user = userEvent.setup();
    renderWithProviders(<AdminPage />, {
      withAuthProvider: true,
      withLeagueSeasonProvider: false,
    });

    await user.type(await screen.findByLabelText(/Username/i), 'admin');
    await user.type(screen.getByLabelText(/Password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: /Log In/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Invalid credentials/i);
    expect(screen.queryByRole('heading', { name: 'Admin' })).not.toBeInTheDocument();
  });

  it('renders signed-in state and logs out', async () => {
    authMeMock.mockResolvedValue(adminUser);
    logoutMock.mockResolvedValue(undefined);

    const user = userEvent.setup();
    renderWithProviders(<AdminPage />, {
      withAuthProvider: true,
      withLeagueSeasonProvider: false,
    });

    expect(await screen.findByRole('heading', { name: 'Admin' })).toBeInTheDocument();
    expect(screen.getByText(/Signed in as admin/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Log out/i }));

    await waitFor(() => {
      expect(logoutMock).toHaveBeenCalled();
    });
    expect(await screen.findByRole('dialog', { name: /Admin Login/i })).toBeInTheDocument();
  });
});
