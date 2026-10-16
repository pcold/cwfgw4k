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
import LateRowBetsPage from './LateRowBetsPage';

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

function buildReport(): WeeklyReport {
  return {
    scope: 'season',
    tournament: {
      name: null,
      startDate: null,
      endDate: null,
      status: null,
      payoutMultiplier: 1,
      week: null,
    },
    teams: [
      {
        teamId: 't-1',
        teamName: 'Aces',
        cells: [],
        topTenEarnings: 0,
        weeklyTotal: 0,
        previous: 0,
        subtotal: 0,
        topTenCount: 0,
        topTenMoney: 0,
        sideBets: 0,
        totalCash: 100,
      },
    ],
    undraftedTopTens: [],
    sideBetRules: { amountPerTeam: 20, rounds: [2, 3] },
    sideBetDetail: [2, 3].map((round) => ({
      round,
      teams: [
        {
          teamId: 't-1',
          teamName: 'Aces',
          golferName: 'Scottie',
          cumulativeEarnings: 500,
          payout: 0,
          outcome: 'won',
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
function buildReportWithoutSideBetFacts(): unknown {
  const { sideBetRules: _rules, sideBetDetail, ...rest } = buildReport();
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

describe('LateRowBetsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    currentTournamentMock.mockReset();
    seasonReportMock.mockReset();
    tournamentReportMock.mockReset();
  });

  it('loads the season report by default (All Tournaments) when the season has no tournaments yet', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport());

    renderWithProviders(<LateRowBetsPage />);

    expect(await screen.findByRole('heading', { name: 'Late Row Bets' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(seasonReportMock).toHaveBeenCalledWith('sn-1');
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('asks live for the tournament in play and only the finalized season report at All Tournaments', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([{ ...tournament, status: 'in_progress' }]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-1' });
    seasonReportMock.mockResolvedValue(buildReport());
    tournamentReportMock.mockResolvedValue(buildReport());

    const user = userEvent.setup();
    renderWithProviders(<LateRowBetsPage />);

    await screen.findByRole('heading', { name: 'Round 2' });
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true);

    await user.selectOptions(screen.getByLabelText(/Through/i), '');

    await waitFor(() => expect(seasonReportMock).toHaveBeenCalledWith('sn-1'));
  });

  it('opens on the tournament the server names, not the first unfinalized one in the list', async () => {
    const first: Tournament = { ...tournament, status: 'upcoming' };
    const second: Tournament = { ...tournament, id: 'tn-2', status: 'upcoming' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([first, second]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-2' });
    tournamentReportMock.mockResolvedValue(buildReport());

    renderWithProviders(<LateRowBetsPage />);

    await screen.findByRole('heading', { name: 'Round 2' });
    expect(screen.getByLabelText<HTMLSelectElement>(/Through/i).value).toBe('tn-2');
    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-2', true);
    expect(tournamentReportMock).not.toHaveBeenCalledWith('sn-1', 'tn-1', expect.anything());
    expect(seasonReportMock).not.toHaveBeenCalled();
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
    seasonReportMock.mockResolvedValue(buildReport());
    tournamentReportMock.mockResolvedValue(buildReport());

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
        <LateRowBetsPage />
      </>,
    );

    await user.selectOptions(await screen.findByLabelText(/Through/i), spring.id);
    await waitFor(() => expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true));
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-2');

    await waitFor(() => expect(tournamentReportMock).toHaveBeenCalledWith('sn-2', 'tn-fall', true));
    expect(tournamentReportMock).not.toHaveBeenCalledWith('sn-2', 'tn-1', expect.anything());
  });

  it('shows the error, and fetches nothing, when the server cannot name the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockRejectedValue(new Error('down'));

    renderWithProviders(<LateRowBetsPage />);

    expect(await screen.findByText(/Failed to load current tournament/i)).toBeInTheDocument();
    expect(seasonReportMock).not.toHaveBeenCalled();
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it('fetches no report while the server has not yet named the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockReturnValue(new Promise(() => {}));

    renderWithProviders(<LateRowBetsPage />);

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
        <LateRowBetsPage />
        <QueryAnswered
          queryKey={queryKeys.tournaments.current('sn-1')}
          fetcher={() => currentTournamentMock('sn-1')}
        />
      </>,
    );

    expect(await screen.findByText(/Loading tournaments/i)).toBeInTheDocument();
    expect(await screen.findByText('answered')).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Through$/i)).not.toBeInTheDocument();
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

    renderWithProviders(<LateRowBetsPage />);

    const loading = await screen.findByText(/Loading current tournament/i);
    answer.release();
    await waitForElementToBeRemoved(loading);
    expect(screen.queryByLabelText(/^Through$/i)).not.toBeInTheDocument();
    expect(seasonReportMock).not.toHaveBeenCalled();
    expect(tournamentReportMock).not.toHaveBeenCalled();
  });

  it("shows the season's own side-bet amount and rounds, not the defaults", async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    seasonReportMock.mockResolvedValue(buildReport());

    renderWithProviders(<LateRowBetsPage />);

    expect(
      await screen.findByText(/^\+\$20\.00 per team in each of draft rounds 2 and 3\./),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Round 3' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Round 5' })).not.toBeInTheDocument();
  });

  it('renders the rounds of a report cached before the rules were sent, without a rules sentence', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([]);
    currentTournamentMock.mockResolvedValue({ type: 'no_tournaments' });
    // @ts-expect-error -- a body cached before the server sent these fields no longer satisfies WeeklyReport.
    seasonReportMock.mockResolvedValue(buildReportWithoutSideBetFacts());

    renderWithProviders(<LateRowBetsPage />);

    expect(await screen.findByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(screen.getAllByText('Scottie')).toHaveLength(2);
    expect(screen.queryByText(/per team in/)).not.toBeInTheDocument();
  });

  it('switches to the per-tournament report when a tournament is selected', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-1' });
    seasonReportMock.mockResolvedValue(buildReport());
    tournamentReportMock.mockResolvedValue(buildReport());

    const user = userEvent.setup();
    renderWithProviders(<LateRowBetsPage />);

    await screen.findByRole('heading', { name: 'Round 2' });
    const select = screen.getByLabelText(/Through/i);
    await user.selectOptions(select, 'tn-1');

    expect(tournamentReportMock).toHaveBeenCalledWith('sn-1', 'tn-1', true);
  });

  it('shows an empty state when there are no leagues', async () => {
    leaguesMock.mockResolvedValue([]);
    renderWithProviders(<LateRowBetsPage />);
    expect(await screen.findByText(/No leagues configured/i)).toBeInTheDocument();
  });

  it('surfaces an error when the leagues query fails', async () => {
    leaguesMock.mockRejectedValue(new Error('network down'));
    renderWithProviders(<LateRowBetsPage />);
    expect(await screen.findByText(/Failed to load leagues/i)).toBeInTheDocument();
  });

  it('surfaces an error when the report query fails', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-1' });
    seasonReportMock.mockRejectedValue(new Error('kaboom'));

    renderWithProviders(<LateRowBetsPage />);
    expect(await screen.findByText(/Failed to load report/i)).toBeInTheDocument();
  });
});
