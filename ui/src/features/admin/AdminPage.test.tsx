import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import type { api } from '@/shared/api/client';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { ApiError } from '@/shared/api/client';
import { notifySessionExpired } from '@/shared/api/sessionExpiry';
import { TAP_TARGET_CLASS } from '@/shared/components/formControls';
import { pageTitleRow } from '@/shared/test/pageTitleRow';
import AdminPage from './AdminPage';

const authMeMock = vi.fn<typeof api.authMe>();
const loginMock = vi.fn<typeof api.login>();
const logoutMock = vi.fn<typeof api.logout>();

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
    dataHealth: vi.fn().mockResolvedValue({
      rosteredTwins: { count: 0, rows: [] },
      rosteredWithoutEspnId: { count: 0, rows: [] },
      blankFirstNames: { count: 0, rows: [] },
    }),
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

  describe('routed at /admin', () => {
    function renderRouted(): void {
      renderWithProviders(
        <Routes>
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/" element={<p>Home page</p>} />
        </Routes>,
        { initialPath: '/admin', withAuthProvider: true, withLeagueSeasonProvider: false },
      );
    }

    it('stays on the admin page after a successful login', async () => {
      authMeMock.mockResolvedValue(null);
      loginMock.mockResolvedValue(adminUser);
      const user = userEvent.setup();
      renderRouted();

      await user.type(await screen.findByLabelText(/Username/i), 'admin');
      await user.type(screen.getByLabelText(/Password/i), 'hunter2');
      await user.click(screen.getByRole('button', { name: /Log In/i }));

      expect(await screen.findByRole('heading', { name: 'Admin' })).toBeInTheDocument();
      expect(screen.queryByText('Home page')).not.toBeInTheDocument();
    });

    it('leaves for the home page when the login is cancelled', async () => {
      authMeMock.mockResolvedValue(null);
      const user = userEvent.setup();
      renderRouted();

      await user.click(await screen.findByRole('button', { name: 'Cancel' }));

      expect(await screen.findByText('Home page')).toBeInTheDocument();
    });
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

  it('puts Log out in the title row, and no league or season picker', async () => {
    authMeMock.mockResolvedValue(adminUser);
    renderWithProviders(<AdminPage />, {
      withAuthProvider: true,
      withLeagueSeasonProvider: false,
    });

    const titleRow = pageTitleRow(await screen.findByRole('heading', { level: 2, name: 'Admin' }));

    expect(within(titleRow).getByRole('button', { name: 'Log out' })).toBeInTheDocument();
    expect(within(titleRow).queryByRole('combobox')).toBeNull();
  });

  // No season rules are served here, so the season form never mounts; CreateSeasonSection.test pins it.
  it('holds every control the signed-in page renders to the tap-target height, and every field to 16px', async () => {
    authMeMock.mockResolvedValue(adminUser);
    renderWithProviders(<AdminPage />, {
      withAuthProvider: true,
      withLeagueSeasonProvider: false,
    });
    await screen.findByRole('heading', { name: 'Admin' });

    const fields = [
      ...screen.getAllByRole('textbox'),
      ...screen.queryAllByRole('combobox'),
    ];
    for (const control of [...screen.getAllByRole('button'), ...fields]) {
      expect(control).toHaveClass(TAP_TARGET_CLASS);
    }
    for (const field of fields) {
      expect(field).toHaveClass('text-base');
    }
    expect(screen.getByLabelText(/Roster file/i)).toHaveClass(TAP_TARGET_CLASS);
  });

  // The client reports a mid-session 401 through `sessionExpiry`, which this file leaves real.
  describe('when a request reports the session expired', () => {
    async function renderSignedIn(): Promise<void> {
      authMeMock.mockResolvedValue(adminUser);
      renderWithProviders(<AdminPage />, { withAuthProvider: true, withLeagueSeasonProvider: false });
      await screen.findByRole('heading', { name: 'Admin' });
    }

    function reportExpiry(times = 1): void {
      act(() => {
        for (let report = 0; report < times; report += 1) notifySessionExpired();
      });
    }

    function holdTheReask(): (answer: Awaited<ReturnType<typeof api.authMe>>) => void {
      let answerReask: (answer: Awaited<ReturnType<typeof api.authMe>>) => void = () => undefined;
      authMeMock.mockReturnValue(
        new Promise((resolve) => {
          answerReask = resolve;
        }),
      );
      return (answer) => {
        act(() => {
          answerReask(answer);
        });
      };
    }

    it('returns the admin to the login modal instead of leaving refused sections on screen', async () => {
      await renderSignedIn();

      authMeMock.mockResolvedValue(null);
      reportExpiry();

      expect(await screen.findByRole('dialog', { name: /Admin Login/i })).toBeInTheDocument();
      expect(screen.queryByRole('heading', { name: 'Admin' })).not.toBeInTheDocument();
    });

    it('asks the server once for a burst of refused requests', async () => {
      await renderSignedIn();

      const answerReask = holdTheReask();
      reportExpiry(3);
      answerReask(null);
      await screen.findByRole('dialog', { name: /Admin Login/i });
      reportExpiry(2);

      expect(screen.getByRole('dialog', { name: /Admin Login/i })).toBeInTheDocument();
      expect(authMeMock).toHaveBeenCalledTimes(2);
    });

    it('keeps a session the server still honours, and the input typed into it', async () => {
      const user = userEvent.setup();
      await renderSignedIn();
      await user.type(screen.getByLabelText('New league name'), 'Castlewood');

      const answerReask = holdTheReask();
      reportExpiry();
      await waitFor(() => {
        expect(authMeMock).toHaveBeenCalledTimes(2);
      });
      answerReask(adminUser);

      expect(await screen.findByLabelText('New league name')).toHaveValue('Castlewood');
      expect(screen.queryByRole('dialog', { name: /Admin Login/i })).not.toBeInTheDocument();
    });
  });
});
