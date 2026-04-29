import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PlayerLinksPanel from './PlayerLinksPanel';
import type {
  Golfer,
  TournamentCompetitorListing,
} from '@/shared/api/types';

const competitorsMock = vi.fn();
const golfersMock = vi.fn();
const upsertMock = vi.fn();
const deleteMock = vi.fn();
const onCloseMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: vi.fn().mockResolvedValue([]),
    seasons: vi.fn().mockResolvedValue([]),
    tournamentCompetitors: (id: string) => competitorsMock(id),
    golfers: (search?: string) => golfersMock(search),
    upsertTournamentPlayerOverride: (
      tournamentId: string,
      body: { espnCompetitorId: string; golferId: string },
    ) => upsertMock(tournamentId, body),
    deleteTournamentPlayerOverride: (tournamentId: string, espnCompetitorId: string) =>
      deleteMock(tournamentId, espnCompetitorId),
  },
  ApiError: class ApiError extends Error {},
}));

const mattGolfer: Golfer = {
  id: 'g-matt',
  pgaPlayerId: null,
  firstName: 'Matt',
  lastName: 'Fitzpatrick',
  country: null,
  worldRanking: null,
  active: true,
  updatedAt: '2026-01-01T00:00:00Z',
};
const alexGolfer: Golfer = { ...mattGolfer, id: 'g-alex', firstName: 'Alex' };
const otherGolfer: Golfer = {
  ...mattGolfer,
  id: 'g-scott',
  firstName: 'Scottie',
  lastName: 'Scheffler',
};

function buildListing(overrides: Partial<TournamentCompetitorListing> = {}): TournamentCompetitorListing {
  return {
    tournamentId: 'tn-1',
    isFinalized: false,
    competitors: [
      {
        espnCompetitorId: 'comp-alex',
        name: 'Alex Fitzpatrick',
        position: 1,
        isTeamPartner: false,
        linkedGolfer: alexGolfer,
        hasOverride: false,
      },
      {
        espnCompetitorId: 'team:1:1',
        name: 'Fitzpatrick',
        position: 1,
        isTeamPartner: true,
        linkedGolfer: mattGolfer,
        hasOverride: true,
      },
      {
        espnCompetitorId: 'comp-nobody',
        name: 'Nobody Special',
        position: 2,
        isTeamPartner: false,
        linkedGolfer: null,
        hasOverride: false,
      },
    ],
    ...overrides,
  };
}

function renderPanel(): void {
  renderWithProviders(
    <PlayerLinksPanel tournamentId="tn-1" seasonId="sn-1" onClose={onCloseMock} />,
    { withAuthProvider: false, withLeagueSeasonProvider: false },
  );
}

describe('PlayerLinksPanel', () => {
  beforeEach(() => {
    competitorsMock.mockReset();
    golfersMock.mockReset();
    upsertMock.mockReset();
    deleteMock.mockReset();
    onCloseMock.mockReset();
    golfersMock.mockResolvedValue([mattGolfer, alexGolfer, otherGolfer]);
  });

  it('renders one row per competitor and tags the manual-override row', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    renderPanel();

    expect(await screen.findByText('Alex Fitzpatrick')).toBeInTheDocument();
    expect(screen.getByText('Fitzpatrick')).toBeInTheDocument();
    expect(screen.getByText('Nobody Special')).toBeInTheDocument();
    expect(screen.getByText(/manual override/i)).toBeInTheDocument();
  });

  it('saves a new link when the user picks a different golfer', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    upsertMock.mockResolvedValue({
      tournamentId: 'tn-1',
      espnCompetitorId: 'comp-nobody',
      golferId: otherGolfer.id,
    });
    const user = userEvent.setup();
    renderPanel();

    const nobodySelect = await screen.findByLabelText(/Link to golfer/i, {
      selector: '#golfer-select-comp-nobody',
    });
    await user.selectOptions(nobodySelect, otherGolfer.id);

    const nobodyRow = nobodySelect.closest('li');
    if (!nobodyRow) throw new Error('competitor row not found');
    const saveButton = within(nobodyRow as HTMLElement).getByRole('button', { name: /Save link/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(upsertMock).toHaveBeenCalledWith('tn-1', {
        espnCompetitorId: 'comp-nobody',
        golferId: otherGolfer.id,
      });
    });
  });

  it('keeps the Save button disabled until the user changes the selection', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    renderPanel();

    const alexSelect = await screen.findByLabelText(/Link to golfer/i, {
      selector: '#golfer-select-comp-alex',
    });
    const alexRow = alexSelect.closest('li');
    if (!alexRow) throw new Error('competitor row not found');
    const saveButton = within(alexRow as HTMLElement).getByRole('button', { name: /Save link/i });
    expect(saveButton).toBeDisabled();
  });

  it('clears an override via the Clear button on rows where one exists', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    deleteMock.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPanel();

    await screen.findByText('Fitzpatrick');
    const clearButton = screen.getByRole('button', { name: /Clear override/i });
    await user.click(clearButton);

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith('tn-1', 'team:1:1');
    });
  });

  it('shows a Clear button only on rows that already carry an override', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    renderPanel();
    await screen.findByText('Fitzpatrick');

    const clearButtons = screen.getAllByRole('button', { name: /Clear override/i });
    expect(clearButtons).toHaveLength(1);
  });

  it('renders the locked notice and disables every control when the tournament is finalized', async () => {
    competitorsMock.mockResolvedValue(buildListing({ isFinalized: true }));
    renderPanel();

    expect(await screen.findByRole('status')).toHaveTextContent(/links locked/i);
    for (const select of screen.getAllByRole('combobox')) {
      expect(select).toBeDisabled();
    }
    for (const button of screen.getAllByRole('button', { name: /Save link/i })) {
      expect(button).toBeDisabled();
    }
    expect(screen.getByRole('button', { name: /Clear override/i })).toBeDisabled();
  });

  it('surfaces a server error when the upsert fails', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    upsertMock.mockRejectedValue(new Error('golfer g-bogus not found'));
    const user = userEvent.setup();
    renderPanel();

    const select = await screen.findByLabelText(/Link to golfer/i, {
      selector: '#golfer-select-comp-nobody',
    });
    await user.selectOptions(select, otherGolfer.id);
    const row = select.closest('li');
    if (!row) throw new Error('competitor row not found');
    await user.click(within(row as HTMLElement).getByRole('button', { name: /Save link/i }));

    expect(await within(row as HTMLElement).findByRole('alert')).toHaveTextContent(
      /golfer g-bogus not found/i,
    );
  });

  it('closes via the Close button', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText('Alex Fitzpatrick');

    await user.click(screen.getByRole('button', { name: /Close panel/i }));
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });

  it('renders an empty-state message when ESPN has no competitors yet', async () => {
    competitorsMock.mockResolvedValue(buildListing({ competitors: [] }));
    renderPanel();
    expect(
      await screen.findByText(/No ESPN competitors found for this tournament/i),
    ).toBeInTheDocument();
  });
});
