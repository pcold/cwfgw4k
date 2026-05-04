import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PlayerRankingsPage from './PlayerRankingsPage';
import type {
  League,
  PlayerRankings,
  PlayerRankingsRow,
  Season,
  Tournament,
} from '@/shared/api/types';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const tournamentsMock = vi.fn();
const playerRankingsMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    tournaments: (id: string) => tournamentsMock(id),
    playerRankings: (seasonId: string, live: boolean, throughTournamentId?: string) =>
      playerRankingsMock(seasonId, live, throughTournamentId),
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
  tieFloor: 1,
  sideBetAmount: 15,
  maxTeams: 13,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function tournament(overrides: Partial<Tournament>): Tournament {
  return {
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
    ...overrides,
  };
}

function row(overrides: Partial<PlayerRankingsRow>): PlayerRankingsRow {
  return {
    key: 'g:g-1',
    golferId: 'g-1',
    name: 'Scottie Scheffler',
    topTens: 1,
    totalEarnings: 18,
    teamName: 'Aces',
    draftRound: 1,
    ...overrides,
  };
}

function rankings(overrides: Partial<PlayerRankings>): PlayerRankings {
  return {
    players: [],
    live: false,
    ...overrides,
  };
}

describe('PlayerRankingsPage', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    tournamentsMock.mockReset();
    playerRankingsMock.mockReset();
  });

  it('renders rows from the player-rankings endpoint', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A', startDate: '2026-03-01' });
    const tournB = tournament({ id: 'tn-b', name: 'Open B', startDate: '2026-03-15' });

    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA, tournB]);
    playerRankingsMock.mockResolvedValue(
      rankings({
        players: [
          row({ key: 'g:g-1', golferId: 'g-1', name: 'Scottie Scheffler', topTens: 2, totalEarnings: 30 }),
          row({
            key: 'u:P. Mickelson',
            golferId: null,
            name: 'P. Mickelson',
            topTens: 1,
            totalEarnings: 8,
            teamName: null,
            draftRound: null,
          }),
        ],
      }),
    );

    renderWithProviders(<PlayerRankingsPage />);

    expect(
      await screen.findByRole('heading', { name: /Player Rankings/i }),
    ).toBeInTheDocument();
    const rows = await screen.findAllByRole('row');
    const dataRows = rows.slice(1);
    expect(dataRows).toHaveLength(2);

    const scheffler = within(dataRows[0]).getAllByRole('cell');
    expect(scheffler[1]).toHaveTextContent('Scottie Scheffler');
    expect(scheffler[2]).toHaveTextContent('2');
    expect(scheffler[3]).toHaveTextContent('$30');
    expect(scheffler[4]).toHaveTextContent('Aces');
    expect(scheffler[5]).toHaveTextContent('1');

    const phil = within(dataRows[1]).getAllByRole('cell');
    expect(phil[1]).toHaveTextContent('P. Mickelson');
    expect(phil[4]).toHaveTextContent('undrafted');
  });

  it('refetches with a different through param when the selector changes', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A', startDate: '2026-03-01' });
    const tournB = tournament({ id: 'tn-b', name: 'Open B', startDate: '2026-03-15' });

    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA, tournB]);
    playerRankingsMock.mockImplementation((_s: string, _live: boolean, throughId?: string) => {
      const total = throughId === 'tn-a' ? 18 : 30;
      return Promise.resolve(
        rankings({
          players: [row({ totalEarnings: total })],
        }),
      );
    });

    const user = userEvent.setup();
    renderWithProviders(<PlayerRankingsPage />);
    await screen.findByText('Scottie Scheffler');
    expect(within(screen.getAllByRole('row')[1]).getAllByRole('cell')[3]).toHaveTextContent(
      '$30.00',
    );

    const select = screen.getByLabelText(/Through/i);
    await user.selectOptions(select, 'tn-a');

    await screen.findByText('$18.00');
    expect(playerRankingsMock).toHaveBeenCalledWith('sn-1', false, 'tn-a');
  });

  it('defaults the through-selector to the earliest non-finalized tournament', async () => {
    const completed = tournament({
      id: 'tn-completed',
      startDate: '2026-03-01',
      status: 'completed',
    });
    const upcoming = tournament({
      id: 'tn-upcoming',
      startDate: '2026-04-01',
      status: 'upcoming',
    });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([completed, upcoming]);
    playerRankingsMock.mockResolvedValue(rankings({}));

    renderWithProviders(<PlayerRankingsPage />);

    await screen.findByRole('option', { name: /tn-upcoming|Sample Open.*upcoming/i });
    const select = screen.getByLabelText(/Through/i) as HTMLSelectElement;
    expect(select.value).toBe('tn-upcoming');
  });

  it('shows an empty state when no players have a top 10', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A' });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA]);
    playerRankingsMock.mockResolvedValue(rankings({ players: [] }));

    renderWithProviders(<PlayerRankingsPage />);
    expect(
      await screen.findByText(/No players with a top 10 finish yet/i),
    ).toBeInTheDocument();
  });

  it('surfaces an error when the player-rankings query fails', async () => {
    const tournA = tournament({ id: 'tn-a', name: 'Open A' });
    leaguesMock.mockResolvedValue([league]);
    seasonsMock.mockResolvedValue([season]);
    tournamentsMock.mockResolvedValue([tournA]);
    playerRankingsMock.mockRejectedValue(new Error('boom'));

    renderWithProviders(<PlayerRankingsPage />);
    expect(await screen.findByText(/Failed to load player rankings/i)).toBeInTheDocument();
  });
});
