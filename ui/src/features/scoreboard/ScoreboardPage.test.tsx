import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import OpenQuery from '@/shared/test/OpenQuery';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeTournament, makeWonLostTotals } from '@/shared/test/fixtures';
import type { League, Season, Tournament, WeeklyReport } from '@/shared/api/types';
import ScoreboardPage from './ScoreboardPage';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const seasonReportMock = vi.fn<typeof api.seasonReport>();
const tournamentsMock = vi.fn<typeof api.tournaments>();
const currentTournamentMock = vi.fn<typeof api.currentTournament>();
const tournamentReportMock = vi.fn<typeof api.tournamentReport>();
const authMeMock = vi.fn<typeof api.authMe>();
const finalizeMock = vi.fn<typeof api.finalizeTournament>();
const competitorsMock = vi.fn<typeof api.tournamentCompetitors>();
const golfersMock = vi.fn<typeof api.golfers>();
const playerRankingsMock = vi.fn<typeof api.playerRankings>();
const golferHistoryMock = vi.fn<typeof api.golferHistory>();
const otherTournamentReportMock = vi.fn();
const rankingsMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: (id: string) => seasonReportMock(id),
    rankings: vi.fn(),
    rosters: vi.fn(),
    tournaments: (id: string) => tournamentsMock(id),
    currentTournament: (id: string) => currentTournamentMock(id),
    tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
      tournamentReportMock(seasonId, tournamentId, live),
    authMe: () => authMeMock(),
    finalizeTournament: (id: string) => finalizeMock(id),
    tournamentCompetitors: (id: string) => competitorsMock(id),
    golfers: () => golfersMock(),
    playerRankings: (seasonId: string, live: boolean, throughTournamentId?: string) =>
      playerRankingsMock(seasonId, live, throughTournamentId),
    golferHistory: (seasonId: string, golferId: string) => golferHistoryMock(seasonId, golferId),
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
  tieFloor: 1,
  sideBetAmount: 15,
  maxTeams: 13,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
const completedTournament: Tournament = {
  id: 'tn-completed',
  pgaTournamentId: null,
  name: 'Sample Open',
  seasonId: 'sn-1',
  startDate: '2026-03-01',
  endDate: '2026-03-04',
  courseName: null,
  status: 'completed',
  purseAmount: null,
  payoutMultiplier: 1,
  week: '9',
  createdAt: '2026-01-01T00:00:00Z',
};
const activeTournament: Tournament = {
  ...completedTournament,
  id: 'tn-active',
  name: 'Current Open',
  status: 'in_progress',
  week: '10',
};

function buildReport(name: string): WeeklyReport {
  return {
    scope: 'tournament',
    tournament: {
      name,
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
        cells: [],
        topTenEarnings: 0,
        weeklyTotal: 0,
        previous: 0,
        subtotal: 0,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: 0,
        totalCash: 0,
      },
    ],
    undraftedTopTens: [],
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: ['team-1'],
  };
}

// Copies the key and fetcher WeeklyReportPage and LateRowBetsPage use on "All Tournaments". A key
// that drifts from theirs leaves the finalize test passing against a query no page holds.
function OpenSeasonReport() {
  useQuery({
    queryKey: queryKeys.report.detail('sn-1', '', false),
    queryFn: () => api.seasonReport('sn-1'),
  });
  return null;
}

// As OpenSeasonReport: the key and fetcher of PlayerRankingsPage on "All Tournaments", and of
// GolferHistoryModal.
function OpenPlayerRankings() {
  useQuery({
    queryKey: queryKeys.playerRankings.detail('sn-1', null, false),
    queryFn: () => api.playerRankings('sn-1', false, undefined),
  });
  return null;
}

function OpenGolferHistory() {
  useQuery({
    queryKey: queryKeys.golferHistory.detail('sn-1', 'g-1'),
    queryFn: () => api.golferHistory('sn-1', 'g-1'),
  });
  return null;
}

describe('ScoreboardPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    seasonReportMock.mockReset();
    tournamentsMock.mockReset();
    currentTournamentMock.mockReset();
    tournamentReportMock.mockReset();
    authMeMock.mockReset();
    finalizeMock.mockReset();
    competitorsMock.mockReset();
    golfersMock.mockReset();
    playerRankingsMock.mockReset();
    golferHistoryMock.mockReset();
    otherTournamentReportMock.mockReset();
    rankingsMock.mockReset();
    authMeMock.mockResolvedValue(null);
    competitorsMock.mockResolvedValue({
      tournamentId: 'tn-active',
      isFinalized: false,
      competitors: [],
    });
    golfersMock.mockResolvedValue([]);
  });

  it('auto-selects an active tournament over a completed one', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament, completedTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-active' ? 'Current Open' : 'Sample Open')),
    );

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    expect(await screen.findByRole('heading', { name: /Current Open/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-active', true);
    });
  });

  it('defaults to the most recent tournament when every event is completed', async () => {
    const earlier = { ...completedTournament, id: 'tn-early', name: 'Early Open', week: '1' };
    const latest = { ...completedTournament, id: 'tn-latest', name: 'Latest Open', week: '20' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([earlier, latest]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-latest' });
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-latest' ? 'Latest Open' : 'Early Open')),
    );

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    expect(await screen.findByRole('heading', { name: /Latest Open/i })).toBeInTheDocument();
    await waitFor(() => {
      // Every tournament is completed → live overlay is suppressed.
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-latest', false);
    });
  });

  it('opens on the tournament the server names, not the first unfinalized one in the list', async () => {
    const opposite: Tournament = { ...activeTournament, id: 'tn-active-b', name: 'Opposite Field Open' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament, opposite]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active-b' });
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-active-b' ? 'Opposite Field Open' : 'Current Open')),
    );

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    expect(await screen.findByRole('heading', { name: /Opposite Field Open/i })).toBeInTheDocument();
    expect(screen.getByLabelText<HTMLSelectElement>(/Tournament/i).value).toBe('tn-active-b');
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-active-b', true);
    expect(tournamentReportMock).not.toHaveBeenCalledWith('sn-1', 'tn-active', expect.anything());
  });

  it('fetches no report while the server has not yet named the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockReturnValue(new Promise(() => {}));

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    expect(await screen.findByText(/Loading current tournament/i)).toBeInTheDocument();
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('refetches the report when the user picks a different tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament, completedTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(buildReport(id === 'tn-active' ? 'Current Open' : 'Sample Open')),
    );

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Current Open/i });
    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, 'tn-completed');

    expect(await screen.findByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
    // The user switched to a completed tournament → live overlay is suppressed.
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-completed', false);
  });

  it('keeps the live overlay eligible for a second non-completed tournament in the same week', async () => {
    // Two-tournament week: AdminService.assignWeeks suffixes the labels as
    // "Na" / "Nb" when two events share an ISO week, so the earliest event
    // and its opposite-field partner have *different* week strings (e.g.
    // "10a" vs "10b") that share the same numeric prefix. The eligibility
    // check must compare on the prefix, not on the full label, otherwise
    // the opposite-field event silently drops the live overlay.
    const truist: Tournament = { ...activeTournament, week: '10a' };
    const oneFlight: Tournament = {
      ...activeTournament,
      id: 'tn-active-b',
      name: 'Opposite Field Open',
      week: '10b',
    };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([truist, oneFlight]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve(
        buildReport(id === 'tn-active' ? 'Current Open' : 'Opposite Field Open'),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Current Open/i });
    await waitFor(() => {
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-active', true);
    });

    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, 'tn-active-b');

    expect(
      await screen.findByRole('heading', { name: /Opposite Field Open/i }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-active-b', true);
    });
    expect(screen.getByRole('checkbox', { name: /Live overlay/i })).toBeInTheDocument();
  });

  it('shows an empty state when the season has no tournaments', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });
    expect(
      await screen.findByText(/No tournaments scheduled for this season/i),
    ).toBeInTheDocument();
  });

  it('shows the Finalize Results button for an admin viewing an in-progress tournament', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open'),
      tournament: {
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });
    finalizeMock.mockResolvedValue(makeTournament());

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    const button = await screen.findByRole('button', { name: /Finalize Results/i });
    await user.click(button);
    await waitFor(() => {
      expect(finalizeMock).toHaveBeenCalledWith('tn-active');
    });
  });

  it('refetches an open season report after an admin finalizes a tournament', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open'),
      tournament: {
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });
    seasonReportMock.mockResolvedValue({
      ...buildReport('All Tournaments'),
      scope: 'season',
    });
    finalizeMock.mockResolvedValue(makeTournament());

    const user = userEvent.setup();
    renderWithProviders(
      <>
        <ScoreboardPage />
        <OpenSeasonReport />
      </>,
      { withAuthProvider: true },
    );

    const button = await screen.findByRole('button', { name: /Finalize Results/i });
    await waitFor(() => {
      expect(seasonReportMock).toHaveBeenCalledTimes(1);
    });
    await user.click(button);

    await waitFor(() => {
      expect(seasonReportMock).toHaveBeenCalledTimes(2);
    });
    expect(seasonReportMock).toHaveBeenLastCalledWith('sn-1');
  });

  it.each([
    ['open player rankings', <OpenPlayerRankings key="player-rankings" />, playerRankingsMock],
    ['an open golfer history', <OpenGolferHistory key="golfer-history" />, golferHistoryMock],
    // Another tournament's, because the body resolves readMock to {} and the page cannot render
    // that as its own report.
    [
      'the scoreboard report of another tournament',
      <OpenQuery
        key="other-tournament-report"
        queryKey={queryKeys.tournamentReport.detail('sn-1', 'tn-completed', false)}
        fetcher={otherTournamentReportMock}
      />,
      otherTournamentReportMock,
    ],
    [
      'open rankings',
      <OpenQuery
        key="rankings"
        queryKey={queryKeys.rankings.detail('sn-1', false, 'tn-active')}
        fetcher={rankingsMock}
      />,
      rankingsMock,
    ],
  ])('refetches %s after an admin finalizes a tournament', async (_page, openQuery, readMock) => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open'),
      tournament: {
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });
    readMock.mockResolvedValue({});
    finalizeMock.mockResolvedValue(makeTournament());

    const user = userEvent.setup();
    renderWithProviders(
      <>
        <ScoreboardPage />
        {openQuery}
      </>,
      { withAuthProvider: true },
    );

    const button = await screen.findByRole('button', { name: /Finalize Results/i });
    await waitFor(() => {
      expect(readMock).toHaveBeenCalledTimes(1);
    });
    await user.click(button);

    await waitFor(() => {
      expect(readMock).toHaveBeenCalledTimes(2);
    });
  });

  it('hides the Finalize Results button for unauthenticated users', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open'),
      tournament: {
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Current Open/i });
    expect(
      screen.queryByRole('button', { name: /Finalize Results/i }),
    ).not.toBeInTheDocument();
  });

  it('hides the Finalize Results button for completed tournaments even when admin', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completedTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-completed' });
    tournamentReportMock.mockResolvedValue(buildReport('Sample Open'));

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    await screen.findByRole('heading', { name: /Sample Open/i });
    expect(
      screen.queryByRole('button', { name: /Finalize Results/i }),
    ).not.toBeInTheDocument();
  });

  it('shows an error message when the report fails to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completedTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-completed' });
    tournamentReportMock.mockRejectedValue(new Error('boom'));

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });
    expect(await screen.findByText(/Failed to load scoreboard/i)).toBeInTheDocument();
  });

  it('shows the Manage player links button for an admin and opens the panel on click', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open'),
      tournament: {
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });

    const user = userEvent.setup();
    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });

    const button = await screen.findByRole('button', { name: /Manage player links/i });
    await user.click(button);

    expect(await screen.findByRole('dialog', { name: /Manage player links/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(competitorsMock).toHaveBeenCalledWith('tn-active');
    });
  });

  it('hides the Manage player links button for a logged-in non-admin', async () => {
    authMeMock.mockResolvedValue({
      id: 'u-2',
      username: 'reg',
      role: 'user',
      createdAt: '2026-01-01T00:00:00Z',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([activeTournament]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-active' });
    tournamentReportMock.mockResolvedValue({
      ...buildReport('Current Open'),
      tournament: {
        name: 'Current Open',
        startDate: '2026-03-01',
        endDate: '2026-03-04',
        status: 'in_progress',
        payoutMultiplier: 1,
        week: '10',
      },
    });

    renderWithProviders(<ScoreboardPage />, { withAuthProvider: true });
    await screen.findByRole('heading', { name: /Current Open/i });

    expect(
      screen.queryByRole('button', { name: /Manage player links/i }),
    ).not.toBeInTheDocument();
  });
});
