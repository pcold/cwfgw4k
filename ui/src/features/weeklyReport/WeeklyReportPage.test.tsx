import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, waitForElementToBeRemoved, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import QueryAnswered from '@/shared/test/QueryAnswered';
import { pageTitleRow } from '@/shared/test/pageTitleRow';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import type { League, Season, Tournament, WeeklyReport } from '@/shared/api/types';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
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
    liveEligible: false,
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

    expect(await screen.findByText('All Tournaments · Season totals')).toBeInTheDocument();
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
    tournamentReportMock.mockResolvedValue({ ...buildReport('Sample Open', 'tn-1'), liveEligible: true });

    const user = userEvent.setup();
    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByText('Sample Open · 2026-03-01 – 2026-03-04')).toBeInTheDocument();
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true);
    expect(screen.getByRole('checkbox', { name: /Live overlay/i })).toBeChecked();
    expect(pageTitleRow(await screen.findByRole('button', { name: 'Wk 9 · Sample Open' }))).toContainElement(
      screen.getByRole('heading', { name: 'Weekly Report' }),
    );
    expect(screen.getByLabelText(/Tournament/i).closest('.hidden')).toHaveClass('sm:block');

    const select = screen.getByLabelText(/Tournament/i);
    await user.selectOptions(select, '');

    expect(await screen.findByText('All Tournaments · Season totals')).toBeInTheDocument();
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

    expect(await screen.findByText('All Tournaments · Season totals')).toBeInTheDocument();
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
    tournamentReportMock.mockResolvedValue({ ...buildReport('Second Open', 'tn-2'), liveEligible: true });

    renderWithProviders(<WeeklyReportPage />);

    expect(await screen.findByText('Second Open · 2026-03-01 – 2026-03-04')).toBeInTheDocument();
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
    tournamentReportMock.mockImplementation((_s, id) =>
      Promise.resolve({ ...buildReport('Event', 'tn-1'), liveEligible: id === 'tn-fall' }),
    );

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
    renderWithProviders(<WeeklyReportPage />);

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

    const grid = await screen.findByRole('table');
    expect(within(grid).getByText('ROWS 2-3')).toBeInTheDocument();
    expect(within(grid).getAllByText('($20.00)')).toHaveLength(2);
  });

  it('renders the grid of a report cached before the rules were sent, naming no rounds or amount', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    // @ts-expect-error -- a body cached before the server sent these fields no longer satisfies WeeklyReport.
    seasonReportMock.mockResolvedValue(buildReportWithoutSideBetFacts('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    const grid = await screen.findByRole('table');
    expect(within(grid).getByText('SIDE BETS')).toBeInTheDocument();
    expect(within(grid).getByText('Golfer 8')).toBeInTheDocument();
    expect(within(grid).queryByText(/^\(\$/)).not.toBeInTheDocument();
  });

  it.each([
    { state: 'LIVE', overrides: { live: true, liveEligible: true }, status: 'in_progress', chip: 'Live' },
    { state: 'FINAL', overrides: {}, status: 'completed', chip: 'Final' },
    { state: 'UPCOMING', overrides: {}, status: 'upcoming', chip: 'Upcoming' },
  ])('chips a $status tournament $state in the title row', async ({ overrides, status, chip }) => {
    const event = { ...tournament, status };
    const report = buildReport('Sample Open', 'tn-1');
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([event]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-1' });
    tournamentReportMock.mockResolvedValue({ ...report, ...overrides, tournament: { ...report.tournament, status } });

    renderWithProviders(<WeeklyReportPage />);

    const subtitle = await screen.findByText('Sample Open · 2026-03-01 – 2026-03-04');
    expect(within(pageTitleRow(subtitle)).getByText(chip)).toBeInTheDocument();
  });

  it('chips nothing on the season report, whose tournament has no status', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-1' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    await screen.findByText('All Tournaments · Season totals');
    expect(screen.queryByText(/^(Live|Final|Upcoming)$/)).not.toBeInTheDocument();
  });

  it('enables the Download PDF button once the report loads', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport('All Tournaments'));

    renderWithProviders(<WeeklyReportPage />);

    const button = await screen.findByRole('button', { name: /Download PDF/i });
    await screen.findByText('All Tournaments · Season totals');
    expect(button).toBeEnabled();
    expect(button).toHaveClass(TAP_TARGET_CLASS);
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
    await screen.findByText('All Tournaments · Season totals');
    const button = screen.getByRole('button', { name: /Download PDF/i });
    await user.click(button);

    expect(await screen.findByRole('alert')).toHaveTextContent(/Failed to generate PDF.*autoTable exploded/);
    expect(button).toBeEnabled();
  });

  it("shows a PDF failure in the phone's sheet, and keeps it on the page once the sheet closes", async () => {
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
    await screen.findByText('All Tournaments · Season totals');
    await user.click(screen.getByRole('button', { name: '2026 Season 1' }));
    const sheet = screen.getByRole('dialog', { name: 'League, season and tournament' });
    await user.click(within(sheet).getByRole('button', { name: /Download PDF/i }));

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(/Failed to generate PDF.*autoTable exploded/);
    await user.click(within(sheet).getByRole('button', { name: 'Close' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/Failed to generate PDF.*autoTable exploded/);
    expect(alert.closest('.hidden')).toBeNull();
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
    await screen.findByText('All Tournaments · Season totals');
    const button = screen.getByRole('button', { name: /Download PDF/i });
    await user.click(button);
    await screen.findByRole('alert');
    await user.click(button);

    await waitFor(() => expect(downloadPdfMock).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('switches the phone cards to the team tapped on the strip', async () => {
    const report = buildReport('All Tournaments');
    const [aces] = report.teams;
    if (!aces) throw new Error('the report fixture has no team');
    const birdies = {
      ...aces,
      teamId: 'team-2',
      teamName: 'Birdies',
      cells: aces.cells.map((cell) => ({ ...cell, golferName: `Birdie ${cell.round}` })),
    };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue({ ...report, teams: [aces, birdies] });
    const user = userEvent.setup();

    renderWithProviders(<WeeklyReportPage />);

    const strip = await screen.findByRole('group', { name: 'Team' });
    expect(within(screen.getByRole('list', { name: 'Picks' })).getByText('Golfer 1')).toBeInTheDocument();

    await user.click(within(strip).getByRole('button', { name: 'Birdies' }));

    expect(within(strip).getByRole('button', { name: 'Birdies' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(strip).getByRole('button', { name: 'Aces' })).toHaveAttribute('aria-pressed', 'false');
    const picks = screen.getByRole('list', { name: 'Picks' });
    expect(within(picks).getByText('Birdie 1')).toBeInTheDocument();
    expect(within(picks).queryByText('Golfer 1')).not.toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Team totals' })).toHaveTextContent('Birdies');
  });
});
