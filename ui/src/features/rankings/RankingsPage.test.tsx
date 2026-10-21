import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, waitForElementToBeRemoved, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import QueryAnswered from '@/shared/test/QueryAnswered';
import { pageTitleRow } from '@/shared/test/pageTitleRow';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import type { League, Rankings, RosterTeam, Season, Tournament } from '@/shared/api/types';
import RankingsPage from './RankingsPage';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const rankingsMock = vi.fn<typeof api.rankings>();
const tournamentsMock = vi.fn<typeof api.tournaments>();
const currentTournamentMock = vi.fn<typeof api.currentTournament>();
const rostersMock = vi.fn<typeof api.rosters>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: (id: string, live: boolean, through?: string) =>
      rankingsMock(id, live, through),
    tournaments: (id: string) => tournamentsMock(id),
    currentTournament: (id: string) => currentTournamentMock(id),
    rosters: (id: string) => rostersMock(id),
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
  id: 'tn-7',
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
const rankings: Rankings = {
  teams: [
    {
      teamId: 'team-1',
      teamName: 'Aces',
      subtotal: 120,
      sideBets: 30,
      totalCash: 150,
      series: [50, 100, 150],
    },
  ],
  weeks: ['9', '10', '11'],
  tournamentNames: ['A', 'B', 'C'],
  live: false,
  liveEligible: false,
  cashTotals: { won: 150, lost: 0, net: 150 },
};

const MY_TEAM_KEY = 'cwfg.myTeam';
const WITH_MY_TEAM = { withMyTeamProvider: true };
const newestRoster: RosterTeam[] = [
  { teamId: 'team-1', teamNumber: 1, teamName: 'Aces', picks: [] },
  { teamId: 'team-2', teamNumber: 2, teamName: 'Birdies', picks: [] },
];

function answerLiveEligibleThrough(...eligibleIds: string[]): void {
  rankingsMock.mockImplementation((_id, _live, through) =>
    Promise.resolve({ ...rankings, liveEligible: through !== undefined && eligibleIds.includes(through) }),
  );
}

describe('RankingsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    rankingsMock.mockReset();
    tournamentsMock.mockReset();
    currentTournamentMock.mockReset();
    rostersMock.mockResolvedValue(newestRoster);
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('defaults to All Tournaments when every tournament is finalized', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    const subtitle = await screen.findByText('3 weeks played');
    expect(pageTitleRow(subtitle)).toContainElement(screen.getByRole('heading', { name: /Team Standings/i }));
    const table = screen.getByRole('table');
    expect(within(table).getByText('Aces')).toBeInTheDocument();
    expect(rankingsMock).toHaveBeenCalledWith('sn-1', false, undefined);
  });

  it('passes the selected tournament as the "through" parameter', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await screen.findByText('3 weeks played');
    const select = screen.getByLabelText(/Through/i);
    await user.selectOptions(select, 'tn-7');

    // tn-7 is completed in this test data → effective live is false.
    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', false, 'tn-7');
  });

  it("defaults to the server's unfinalized current tournament", async () => {
    const completed: Tournament = { ...tournament, id: 'tn-completed', status: 'completed' };
    const upcoming: Tournament = {
      ...tournament,
      id: 'tn-upcoming',
      status: 'upcoming',
      startDate: '2026-04-01',
      endDate: '2026-04-04',
      week: '13',
    };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completed, upcoming]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-upcoming' });
    answerLiveEligibleThrough('tn-upcoming');

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await screen.findByText('3 weeks played');
    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', true, 'tn-upcoming');
    expect(pageTitleRow(await screen.findByRole('button', { name: 'Wk 13 · Sample Open' }))).toContainElement(
      screen.getByRole('heading', { name: 'Team Standings' }),
    );
    expect(screen.getByLabelText(/Through/i).closest('.hidden')).toHaveClass('sm:block');
  });

  it('runs through the tournament the server names, not the first unfinalized one in the list', async () => {
    const first: Tournament = { ...tournament, status: 'upcoming' };
    const second: Tournament = { ...tournament, id: 'tn-8', status: 'upcoming', week: '10' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([first, second]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-8' });
    answerLiveEligibleThrough('tn-8');

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await screen.findByText('3 weeks played');
    expect(screen.getByLabelText<HTMLSelectElement>(/Through/i).value).toBe('tn-8');
    expect(rankingsMock).toHaveBeenCalledWith('sn-1', true, 'tn-8');
    expect(rankingsMock).not.toHaveBeenCalledWith('sn-1', expect.anything(), 'tn-7');
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
    answerLiveEligibleThrough('tn-fall');

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
    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await user.selectOptions(await screen.findByLabelText(/Through/i), spring.id);
    await waitFor(() => expect(rankingsMock).toHaveBeenCalledWith('sn-1', false, 'tn-7'));
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-2');

    await waitFor(() => expect(rankingsMock).toHaveBeenCalledWith('sn-2', true, 'tn-fall'));
    expect(rankingsMock).not.toHaveBeenCalledWith('sn-2', expect.anything(), 'tn-7');
  });

  it('shows the error, and fetches nothing, when the server cannot name the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockRejectedValue(new Error('down'));

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    expect(await screen.findByText(/Failed to load current tournament/i)).toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalled();
  });

  it('fetches no rankings while the server has not yet named the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockReturnValue(new Promise(() => {}));

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    expect(await screen.findByText(/Loading current tournament/i)).toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalled();
  });

  it('shows no selector, and fetches nothing, while the tournament list trails the answer', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockReturnValue(new Promise(() => {}));
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(
      <>
        <RankingsPage />
        <QueryAnswered
          queryKey={queryKeys.tournaments.current('sn-1')}
          fetcher={() => currentTournamentMock('sn-1')}
        />
      </>,
      WITH_MY_TEAM,
    );

    expect(await screen.findByText(/Loading tournaments/i)).toBeInTheDocument();
    expect(await screen.findByText('answered')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Through/i)).not.toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalled();
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

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    const loading = await screen.findByText(/Loading current tournament/i);
    answer.release();
    await waitForElementToBeRemoved(loading);
    expect(screen.queryByLabelText(/^Through$/i)).not.toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalled();
  });

  it('shows the live toggle through the tournament in play and asks no live rankings at All Tournaments', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([{ ...tournament, status: 'in_progress' }]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-7' });
    answerLiveEligibleThrough('tn-7');

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await screen.findByText('3 weeks played');
    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', true, 'tn-7');
    expect(screen.getByRole('checkbox', { name: /Live overlay/i })).toBeChecked();

    await user.selectOptions(screen.getByLabelText(/Through/i), '');

    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', false, undefined);
    expect(screen.queryByRole('checkbox', { name: /Live overlay/i })).not.toBeInTheDocument();
  });

  it("runs through All Tournaments picked from the phone's sheet, the summary falling back to the season", async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([{ ...tournament, status: 'in_progress' }]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-7' });
    answerLiveEligibleThrough('tn-7');

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await user.click(await screen.findByRole('button', { name: 'Wk 9 · Sample Open' }));
    const sheet = screen.getByRole('dialog', { name: 'League, season and tournament' });
    await user.selectOptions(within(sheet).getByRole('combobox', { name: 'Through' }), '');

    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', false, undefined);
    expect(screen.getByRole('button', { name: '2026 Season 1' })).toBeInTheDocument();
  });

  it('offers the live toggle through another week when the server says the overlay applies there', async () => {
    const inPlay: Tournament = { ...tournament, status: 'in_progress' };
    const later: Tournament = { ...tournament, id: 'tn-12', status: 'upcoming', week: '12' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([inPlay, later]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-7' });
    answerLiveEligibleThrough('tn-12');

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await screen.findByText('3 weeks played');
    expect(screen.queryByRole('checkbox', { name: /Live overlay/i })).not.toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalledWith('sn-1', true, 'tn-7');

    await user.selectOptions(screen.getByLabelText(/Through/i), 'tn-12');

    expect(await screen.findByRole('checkbox', { name: /Live overlay/i })).toBeChecked();
    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', true, 'tn-12');
  });

  it('marks the standings LIVE when the server overlaid them', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([{ ...tournament, status: 'in_progress' }]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue({ ...rankings, live: true, liveEligible: true });

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    const subtitle = await screen.findByText('3 weeks played');
    expect(within(pageTitleRow(subtitle)).getByText('Live')).toHaveClass('bg-primary');
  });

  it('shows no status chip on standings the server did not overlay', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);

    await screen.findByText('3 weeks played');
    expect(screen.queryByText(/^(Live|Final|Upcoming)$/)).not.toBeInTheDocument();
  });

  describe("the visitor's own team", () => {
    const twoTeams: Rankings = {
      ...rankings,
      teams: [
        ...rankings.teams,
        { teamId: 'team-2', teamName: 'Birdies', subtotal: 10, sideBets: 0, totalCash: 10, series: [0, 5, 10] },
      ],
    };

    async function renderStandings(): Promise<void> {
      leaguesMock.mockResolvedValue([league]);
      seasonsMock.mockResolvedValue([season]);
      tournamentsMock.mockResolvedValue([tournament]);
      currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
      rankingsMock.mockResolvedValue(twoTeams);
      renderWithProviders(<RankingsPage />, WITH_MY_TEAM);
      await screen.findByRole('table');
    }

    it('tags the saved team on its card and its row, and no other', async () => {
      window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"BIRDIES"}');
      await renderStandings();

      expect(within(screen.getByRole('row', { name: /Birdies/ })).getByText('your team')).toBeInTheDocument();
      expect(screen.getAllByText('your team')).toHaveLength(2);
    });

    it.each([
      ['browsing', '{"kind":"browsing"}'],
      ['never asked', null],
    ])('tags nothing when %s', async (_choice, stored) => {
      if (stored !== null) window.localStorage.setItem(MY_TEAM_KEY, stored);
      await renderStandings();

      expect(screen.getByRole('row', { name: /Birdies/ })).toBeInTheDocument();
      expect(screen.queryByText('your team')).toBeNull();
    });
  });

  it('shows an error message when rankings fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RankingsPage />, WITH_MY_TEAM);
    expect(await screen.findByText(/Failed to load standings/i)).toBeInTheDocument();
  });
});
