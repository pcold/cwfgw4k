import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import type { League, Rankings, Season, Tournament } from '@/shared/api/types';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import RankingsPage from './RankingsPage';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const rankingsMock = vi.fn<typeof api.rankings>();
const tournamentsMock = vi.fn<typeof api.tournaments>();
const currentTournamentMock = vi.fn<typeof api.currentTournament>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    seasonReport: vi.fn(),
    rankings: (id: string, live: boolean, through?: string) =>
      rankingsMock(id, live, through),
    tournaments: (id: string) => tournamentsMock(id),
    currentTournament: (id: string) => currentTournamentMock(id),
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
};

describe('RankingsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    rankingsMock.mockReset();
    tournamentsMock.mockReset();
    currentTournamentMock.mockReset();
  });

  it('defaults to All Tournaments when every tournament is finalized', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />);

    expect(await screen.findByRole('heading', { name: /Team Standings/i })).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('Aces')).toBeInTheDocument();
    // Only the earliest non-finalized tournament view triggers live=true.
    // Test data here is fully completed → effective live is false.
    expect(rankingsMock).toHaveBeenCalledWith('sn-1', false, undefined);
  });

  it('passes the selected tournament as the "through" parameter', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />);

    await screen.findByRole('heading', { name: /Team Standings/i });
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
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />);

    await screen.findByRole('heading', { name: /Team Standings/i });
    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', true, 'tn-upcoming');
  });

  it('runs through the tournament the server names, not the first unfinalized one in the list', async () => {
    const first: Tournament = { ...tournament, status: 'upcoming' };
    const second: Tournament = { ...tournament, id: 'tn-8', status: 'upcoming', week: '10' };
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([first, second]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-8' });
    rankingsMock.mockResolvedValue(rankings);

    renderWithProviders(<RankingsPage />);

    await screen.findByRole('heading', { name: /Team Standings/i });
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
    rankingsMock.mockResolvedValue(rankings);

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
        <RankingsPage />
      </>,
    );

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

    renderWithProviders(<RankingsPage />);

    expect(await screen.findByText(/Failed to load current tournament/i)).toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalled();
  });

  it('fetches no rankings while the server has not yet named the current tournament', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockReturnValue(new Promise(() => {}));

    renderWithProviders(<RankingsPage />);

    expect(await screen.findByText(/Loading current tournament/i)).toBeInTheDocument();
    expect(rankingsMock).not.toHaveBeenCalled();
  });

  it('shows the live toggle through the tournament in play and asks no live rankings at All Tournaments', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([{ ...tournament, status: 'in_progress' }]);
    currentTournamentMock.mockResolvedValue({ type: 'unfinalized', tournamentId: 'tn-7' });
    rankingsMock.mockResolvedValue(rankings);

    const user = userEvent.setup();
    renderWithProviders(<RankingsPage />);

    await screen.findByRole('heading', { name: /Team Standings/i });
    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', true, 'tn-7');
    expect(screen.getByRole('checkbox', { name: /Live overlay/i })).toBeChecked();

    await user.selectOptions(screen.getByLabelText(/Through/i), '');

    expect(rankingsMock).toHaveBeenLastCalledWith('sn-1', false, undefined);
    expect(screen.queryByRole('checkbox', { name: /Live overlay/i })).not.toBeInTheDocument();
  });

  it('shows an error message when rankings fail to load', async () => {
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournament]);
    currentTournamentMock.mockResolvedValue({ type: 'season_finalized', tournamentId: 'tn-7' });
    rankingsMock.mockRejectedValue(new Error('nope'));

    renderWithProviders(<RankingsPage />);
    expect(await screen.findByText(/Failed to load standings/i)).toBeInTheDocument();
  });
});
