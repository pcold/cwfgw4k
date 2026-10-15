import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, waitForElementToBeRemoved } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import QueryAnswered from '@/shared/test/QueryAnswered';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import type { League, Season, Tournament, WeeklyReport } from '@/shared/api/types';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import WeeklyReportPage from './WeeklyReportPage';
import type { downloadWeeklyReportPdf } from './weeklyReportPdf';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const tournamentsMock = vi.fn<typeof api.tournaments>();
const currentTournamentMock = vi.fn<typeof api.currentTournament>();
const seasonReportMock = vi.fn<typeof api.seasonReport>();
const tournamentReportMock = vi.fn<typeof api.tournamentReport>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: (...args: Parameters<typeof api.seasonReport>) => seasonReportMock(...args),
    rankings: vi.fn(),
    rosters: vi.fn(),
    tournaments: (id: string) => tournamentsMock(id),
    currentTournament: (id: string) => currentTournamentMock(id),
    tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
      tournamentReportMock(seasonId, tournamentId, live),
  },
  ApiError: class ApiError extends Error {},
}));

const downloadPdfMock = vi.fn<typeof downloadWeeklyReportPdf>();

vi.mock('./weeklyReportPdf', () => ({
  downloadWeeklyReportPdf: (report: WeeklyReport, season: Season | null) =>
    downloadPdfMock(report, season),
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
const tournament: Tournament = {
  id: 'tn-1',
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

function buildReport(name: string, tournamentId: string | null = null): WeeklyReport {
  return {
    scope: tournamentId ? 'tournament' : 'season',
    tournament: {
      name,
      startDate: tournamentId ? '2026-03-01' : null,
      endDate: tournamentId ? '2026-03-04' : null,
      status: tournamentId ? 'completed' : null,
      payoutMultiplier: 1,
      week: tournamentId ? '9' : null,
    },
    teams: [
      {
        teamId: 'team-1',
        teamName: 'Aces',
        cells: [1, 2, 3, 4, 5, 6, 7, 8].map((r) => ({
          round: r,
          golferName: `Golfer ${r}`,
          golferId: `g-${r}`,
          positionStr: null,
          finish: null,
          scoreToPar: null,
          earnings: 0,
          topTens: 0,
          ownershipPct: 100,
          seasonEarnings: 0,
          seasonTopTens: 0,
          pairKey: null,
        })),
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
    sideBetRules: { amountPerTeam: 20, rounds: [2, 3] },
    sideBetDetail: [2, 3].map((round) => ({
      round,
      teams: [
        {
          teamId: 'team-1',
          teamName: 'Aces',
          golferName: `Golfer ${round}`,
          cumulativeEarnings: 0,
          payout: 0,
          outcome: null,
        },
      ],
    })),
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
  };
}

// The body a report cached before the server sent rules, team names and outcomes still serves.
function buildReportWithoutSideBetFacts(name: string): unknown {
  const { sideBetRules: _rules, sideBetDetail, ...rest } = buildReport(name);
  return {
    ...rest,
    sideBetDetail: sideBetDetail.map(({ round, teams }) => ({
      round,
      teams: teams.map(({ teamId, golferName, cumulativeEarnings, payout }) => ({
        teamId,
        golferName,
        cumulativeEarnings,
        payout,
      })),
    })),
  };
}

describe('WeeklyReportPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    currentTournamentMock.mockReset();
    seasonReportMock.mockReset();
    tournamentReportMock.mockReset();
    downloadPdfMock.mockReset();
  });

  it('loads the season report by default (All Tournaments) when the season has no tournaments yet', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /All Tournaments/i })).toBeInTheDocument();
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1');
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it("defaults to the server's current tournament, live, and switches to the finalized season report at All Tournaments", async () => {
    const inProgress = { ...tournament, status: 'in_progress' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([inProgress]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-1' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));
    tournamentReportMock.mockResolvedValue(buildReport('Sample Open', 'tn-1'));

    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /Sample Open/i })).toBeInTheDocument();
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true);
    expect(screen.getByRole('checkbox', { name: /Live overlay/i })).toBeChecked();

    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, '');

    expect(await screen.findByRole('heading', { name: /All Tournaments/i })).toBeInTheDocument();
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1');
    expect(screen.queryByRole('checkbox', { name: /Live overlay/i })).not.toBeInTheDocument();
  });

  it('defaults to All Tournaments when every tournament is already completed', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-1' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /All Tournaments/i })).toBeInTheDocument();
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1');
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('opens on the tournament the server names, not the first unfinalized one in the list', async () => {
    const first = { ...tournament, status: 'upcoming' };
    const second = {
      ...tournament,
      id: 'tn-2',
      name: 'Second Open',
      status: 'upcoming',
      week: '10',
    };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([first, second]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-2' });
    tournamentReportMock.mockResolvedValue(buildReport('Second Open', 'tn-2'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByRole('heading', { name: /Second Open/i })).toBeInTheDocument();
    expect(screen.getByLabelText<HTMLSelectElement>(/Tournament/i).value).toBe('tn-2');
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-2', true);
    expect(tournamentReportMock).not.toHaveBeenCalledWith('sn-1', 'tn-1', expect.anything());
  });

  it("drops a pick the newly selected season lacks and opens on that season's current tournament", async () => {
    const fall: Season = { ...season, id: 'sn-2', name: 'Fall', seasonNumber: 2 };
    const spring: Tournament = { ...tournament, status: 'completed' };
    const autumn: Tournament = {
      ...tournament,
      id: 'tn-fall',
      seasonId: 'sn-2',
      status: 'in_progress',
    };
    seasonReportMock.mockResolvedValue(buildReport('Season'));
    tournamentReportMock.mockResolvedValue(buildReport('Event', 'tn-1'));

    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season, fall]);
    tournamentsMock.mockImplementation((seasonId) =>
      Promise.resolve(seasonId === 'sn-1' ? [spring] : [autumn]),
    );
    currentTournamentMock.mockImplementation((seasonId) =>
      Promise.resolve(
        seasonId === 'sn-1'
          ? { type: 'season_finalized', tournamentId: spring.id }
          : { type: 'unfinalized', tournamentId: autumn.id },
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(
      <>
        <LeagueSeasonPicker />
        <WeeklyReportPage />
      </>,
    );

    await user.selectOptions(await screen.findByLabelText(/Tournament/i), spring.id);
    await waitFor(() => expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', false));
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-2');

    await waitFor(() => expect(tournamentReportMock).toHaveBeenCalledWith('sn-2', 'tn-fall', true));
    expect(tournamentReportMock).not.toHaveBeenCalledWith('sn-2', 'tn-1', expect.anything());
  });

  it('shows the error, and fetches nothing, when the server cannot name the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockRejectedValue(new Error('down'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByText(/Failed to load current tournament/i)).toBeInTheDocument();
    expect(seasonReportMock).not.toHaveBeenCalled();
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('fetches no report while the server has not yet named the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockReturnValue(new Promise(() => {}));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByText(/Loading current tournament/i)).toBeInTheDocument();
    expect(seasonReportMock).not.toHaveBeenCalled();
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('shows no selector, and fetches nothing, while the tournament list trails the answer', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockReturnValue(new Promise(() => {}));
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: tournament.id });

    renderWithProviders(
      <>
        <WeeklyReportPage />
        <QueryAnswered
          queryKey={queryKeys.tournaments.current('sn-1')}
          fetcher={() => currentTournamentMock('sn-1')}
        />
      </>,
    );

    expect(await screen.findByText(/Loading tournaments/i)).toBeInTheDocument();
    expect(await screen.findByText('answered')).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Tournament$/i)).not.toBeInTheDocument();
    expect(seasonReportMock).not.toHaveBeenCalled();
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('selects nothing, and fetches nothing, when the answer names a tournament the list lacks', async () => {
    const answer = { release: (): void => {} };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockReturnValue(
      new Promise((resolve) => {
        answer.release = () => resolve({ type: 'unfinalized', tournamentId: 'tn-not-listed' });
      }),
    );

    renderWithProviders(<WeeklyReportPage />);

    const loading = await screen.findByText(/Loading current tournament/i);
    answer.release();
    await waitForElementToBeRemoved(loading);
    expect(screen.queryByLabelText(/^Tournament$/i)).not.toBeInTheDocument();
    expect(seasonReportMock).not.toHaveBeenCalled();
    expect(tournamentReportMock).not.toHaveBeenCalled();
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
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-1' });
    seasonReportMock.mockRejectedValue(new Error('kaboom'));

    renderWithProviders(<WeeklyReportPage />);
    expect(await screen.findByText(/Failed to load report/i)).toBeInTheDocument();
  });

  it("shows the season's own side-bet amount and rounds, not the defaults", async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByText('ROWS 2-3')).toBeInTheDocument();
    expect(screen.getAllByText('($20.00)')).toHaveLength(2);
  });

  it('renders the grid of a report cached before the rules were sent, naming no rounds or amount', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    // @ts-expect-error -- a body cached before the server sent these fields no longer satisfies WeeklyReport.
    seasonReportMock.mockResolvedValue(buildReportWithoutSideBetFacts('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByText('SIDE BETS')).toBeInTheDocument();
    expect(screen.getByText('Golfer 8')).toBeInTheDocument();
    expect(screen.queryByText(/^\(\$/)).not.toBeInTheDocument();
  });

  it('enables the Download PDF button once the report loads', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    const button = await screen.findByRole('button', { name: /Download PDF/i });
    await screen.findByRole('heading', { name: /All Tournaments/i });
    expect(button).toBeEnabled();
  });

  it('leaves the Download PDF button disabled while the report is loading', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockImplementation(() => new Promise(() => {}));

    renderWithProviders(<WeeklyReportPage />);

    const button = await screen.findByRole('button', { name: /Download PDF/i });
    expect(button).toBeDisabled();
  });

  it('shows an error and re-enables Download PDF when the PDF build fails', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));
    downloadPdfMock.mockImplementation(() => {
      throw new Error('autoTable exploded');
    });

    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportPage />);
    await screen.findByRole('heading', { name: /All Tournaments/i });
    const button = screen.getByRole('button', { name: /Download PDF/i });
    await user.click(button);

    expect(await screen.findByRole('alert')).toHaveTextContent(/Failed to generate PDF.*autoTable exploded/);
    expect(button).toBeEnabled();
  });

  it('clears the PDF error when the next download attempt starts', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));
    downloadPdfMock.mockImplementationOnce(() => {
      throw new Error('autoTable exploded');
    });

    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportPage />);
    await screen.findByRole('heading', { name: /All Tournaments/i });
    const button = screen.getByRole('button', { name: /Download PDF/i });
    await user.click(button);
    await screen.findByRole('alert');
    await user.click(button);

    await waitFor(() => expect(downloadPdfMock).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
