import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import type {
  Golfer,
  TournamentCompetitorListing,
} from '@/shared/api/types';
import PlayerLinksPanel from './PlayerLinksPanel';

const competitorsMock = vi.fn<typeof api.tournamentCompetitors>();
const golfersMock = vi.fn<typeof api.golfers>();
const upsertMock = vi.fn<typeof api.upsertTournamentPlayerOverride>();
const deleteMock = vi.fn<typeof api.deleteTournamentPlayerOverride>();
const createGolferAndLinkMock = vi.fn<typeof api.createGolferAndLink>();
const onCloseMock = vi.fn();
const tournamentReportMock = vi.fn<typeof api.tournamentReport>();
const rankingsMock = vi.fn<typeof api.rankings>();
const playerRankingsMock = vi.fn<typeof api.playerRankings>();

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
    createGolferAndLink: (tournamentId: string, body: { espnCompetitorId: string; name: string }) =>
      createGolferAndLinkMock(tournamentId, body),
    tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
      tournamentReportMock(seasonId, tournamentId, live),
    rankings: (seasonId: string, live: boolean, throughTournamentId?: string) =>
      rankingsMock(seasonId, live, throughTournamentId),
    playerRankings: (seasonId: string, live: boolean, throughTournamentId?: string) =>
      playerRankingsMock(seasonId, live, throughTournamentId),
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

// Each copies the key and fetcher its page uses with the live overlay on: WeeklyReportPage (and
// LateRowBetsPage), RankingsPage, PlayerRankingsPage. A key that drifts from the page's leaves its
// test passing against a query no page holds.
function OpenLiveWeeklyReport() {
  useQuery({
    queryKey: queryKeys.report.detail('sn-1', 'tn-1', true),
    queryFn: () => api.tournamentReport('sn-1', 'tn-1', true),
  });
  return null;
}

function OpenLiveRankings() {
  useQuery({
    queryKey: queryKeys.rankings.detail('sn-1', true, 'tn-1'),
    queryFn: () => api.rankings('sn-1', true, 'tn-1'),
  });
  return null;
}

function OpenLivePlayerRankings() {
  useQuery({
    queryKey: queryKeys.playerRankings.detail('sn-1', 'tn-1', true),
    queryFn: () => api.playerRankings('sn-1', true, 'tn-1'),
  });
  return null;
}

// As OpenLiveWeeklyReport: the key and fetcher of ScoreboardPage.
function OpenLiveScoreboardReport() {
  useQuery({
    queryKey: queryKeys.tournamentReport.detail('sn-1', 'tn-1', true),
    queryFn: () => api.tournamentReport('sn-1', 'tn-1', true),
  });
  return null;
}

function renderPanel(openQuery: ReactElement | null = null): void {
  renderWithProviders(
    <>
      <PlayerLinksPanel tournamentId="tn-1" seasonId="sn-1" onClose={onCloseMock} />
      {openQuery}
    </>,
    { withAuthProvider: false, withLeagueSeasonProvider: false },
  );
}

describe('PlayerLinksPanel', () => {
  beforeEach(() => {
    competitorsMock.mockReset();
    golfersMock.mockReset();
    upsertMock.mockReset();
    deleteMock.mockReset();
    createGolferAndLinkMock.mockReset();
    onCloseMock.mockReset();
    tournamentReportMock.mockReset();
    rankingsMock.mockReset();
    playerRankingsMock.mockReset();
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

  it('shows a link the refetch brings in on a row the admin has not touched', async () => {
    const refetched = buildListing();
    competitorsMock.mockResolvedValueOnce(buildListing()).mockResolvedValue({
      ...refetched,
      competitors: refetched.competitors.map((competitor) =>
        competitor.espnCompetitorId === 'comp-alex' ? { ...competitor, linkedGolfer: otherGolfer } : competitor,
      ),
    });
    upsertMock.mockResolvedValue({ tournamentId: 'tn-1', espnCompetitorId: 'comp-nobody', golferId: otherGolfer.id });
    const user = userEvent.setup();
    renderPanel();

    const nobodySelect = await screen.findByLabelText(/Link to golfer/i, { selector: '#golfer-select-comp-nobody' });
    await user.selectOptions(nobodySelect, otherGolfer.id);
    const nobodyRow = nobodySelect.closest('li');
    if (!nobodyRow) throw new Error('competitor row not found');
    await user.click(within(nobodyRow as HTMLElement).getByRole('button', { name: /Save link/i }));

    const alexSelect = screen.getByLabelText<HTMLSelectElement>(/Link to golfer/i, {
      selector: '#golfer-select-comp-alex',
    });
    await waitFor(() => {
      expect(alexSelect.value).toBe(otherGolfer.id);
    });
  });

  // Widened to any resolving mock: the test counts fetches and never renders the `{}` it resolves.
  it.each<[string, ReactElement, Mock<(...args: never[]) => Promise<unknown>>]>([
    ['an open live weekly report', <OpenLiveWeeklyReport key="weekly" />, tournamentReportMock],
    ['open live rankings', <OpenLiveRankings key="rankings" />, rankingsMock],
    ['open live player rankings', <OpenLivePlayerRankings key="player-rankings" />, playerRankingsMock],
    ['an open live scoreboard report', <OpenLiveScoreboardReport key="scoreboard" />, tournamentReportMock],
  ])('refetches %s after a link is saved', async (_page, openQuery, readMock) => {
    competitorsMock.mockResolvedValue(buildListing());
    upsertMock.mockResolvedValue({
      tournamentId: 'tn-1',
      espnCompetitorId: 'comp-nobody',
      golferId: otherGolfer.id,
    });
    readMock.mockResolvedValue({});
    const user = userEvent.setup();
    renderPanel(openQuery);

    const nobodySelect = await screen.findByLabelText(/Link to golfer/i, {
      selector: '#golfer-select-comp-nobody',
    });
    await waitFor(() => {
      expect(readMock).toHaveBeenCalledTimes(1);
    });
    await user.selectOptions(nobodySelect, otherGolfer.id);
    const nobodyRow = nobodySelect.closest('li');
    if (!nobodyRow) throw new Error('competitor row not found');
    await user.click(within(nobodyRow as HTMLElement).getByRole('button', { name: /Save link/i }));

    await waitFor(() => {
      expect(readMock).toHaveBeenCalledTimes(2);
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

  it('drops an unsaved pick once a clear lands, showing the link the server now holds', async () => {
    const cleared = buildListing();
    competitorsMock.mockResolvedValueOnce(buildListing()).mockResolvedValue({
      ...cleared,
      competitors: cleared.competitors.map((competitor) =>
        competitor.espnCompetitorId === 'team:1:1'
          ? { ...competitor, linkedGolfer: alexGolfer, hasOverride: false }
          : competitor,
      ),
    });
    deleteMock.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPanel();

    const partnerRow = (await screen.findByText('Fitzpatrick')).closest('li');
    if (!partnerRow) throw new Error('partner row not found');
    const partnerSelect = within(partnerRow as HTMLElement).getByLabelText<HTMLSelectElement>(/Link to golfer/i);
    await user.selectOptions(partnerSelect, otherGolfer.id);
    await user.click(screen.getByRole('button', { name: /Clear override/i }));

    await waitFor(() => {
      expect(partnerSelect.value).toBe(alexGolfer.id);
    });
  });

  it('keeps a row linked to a golfer the list leaves out, rather than reading it as unlinked', async () => {
    const retired: Golfer = { ...mattGolfer, id: 'g-retired', firstName: 'Old' };
    const listing = buildListing();
    competitorsMock.mockResolvedValue({
      ...listing,
      competitors: listing.competitors.map((competitor) =>
        competitor.espnCompetitorId === 'comp-nobody' ? { ...competitor, linkedGolfer: retired } : competitor,
      ),
    });
    renderPanel();

    const nobodySelect = await screen.findByLabelText<HTMLSelectElement>(/Link to golfer/i, {
      selector: '#golfer-select-comp-nobody',
    });
    expect(nobodySelect.value).toBe('g-retired');
    expect(within(nobodySelect).getByRole('option', { selected: true })).toHaveTextContent('Fitzpatrick, Old');
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

    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });

  it('renders an empty-state message when ESPN has no competitors yet', async () => {
    competitorsMock.mockResolvedValue(buildListing({ competitors: [] }));
    renderPanel();
    expect(
      await screen.findByText(/No ESPN competitors found for this tournament/i),
    ).toBeInTheDocument();
  });

  it('opens the create-golfer form pre-filled with a partner row\'s lone surname', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    const user = userEvent.setup();
    renderPanel();

    const partnerRow = (await screen.findByText('Fitzpatrick')).closest('li');
    if (!partnerRow) throw new Error('partner row not found');
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));

    const name = within(partnerRow as HTMLElement).getByLabelText<HTMLInputElement>(/Golfer name/i);
    expect(name.value).toBe('Fitzpatrick');
  });

  it('pre-fills the full name from a full-name competitor row', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    const user = userEvent.setup();
    renderPanel();

    const alexRow = (await screen.findByText('Alex Fitzpatrick')).closest('li');
    if (!alexRow) throw new Error('competitor row not found');
    await user.click(within(alexRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));

    const name = within(alexRow as HTMLElement).getByLabelText<HTMLInputElement>(/Golfer name/i);
    expect(name.value).toBe('Alex Fitzpatrick');
  });

  it('creates a new golfer and pins this row to them in exactly one request', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    createGolferAndLinkMock.mockResolvedValue({
      tournamentId: 'tn-1',
      espnCompetitorId: 'team:1:1',
      golferId: 'g-new-fitz',
    });
    const user = userEvent.setup();
    renderPanel();

    const partnerRow = (await screen.findByText('Fitzpatrick')).closest('li');
    if (!partnerRow) throw new Error('partner row not found');
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));

    const name = within(partnerRow as HTMLElement).getByLabelText(/Golfer name/i);
    await user.clear(name);
    await user.type(name, ' Other Fitzpatrick ');
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create & link/i }));

    await waitFor(() => {
      expect(screen.queryByLabelText(/Golfer name/i)).not.toBeInTheDocument();
    });
    expect(createGolferAndLinkMock).toHaveBeenCalledTimes(1);
    expect(createGolferAndLinkMock).toHaveBeenCalledWith('tn-1', {
      espnCompetitorId: 'team:1:1',
      name: 'Other Fitzpatrick',
    });
    expect(upsertMock).not.toHaveBeenCalled();
  });

  it('surfaces the server\'s refusal of a name with no first name', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    createGolferAndLinkMock.mockRejectedValue(new Error('a new golfer needs a first and a last name; got "Fitzpatrick"'));
    const user = userEvent.setup();
    renderPanel();

    const partnerRow = (await screen.findByText('Fitzpatrick')).closest('li');
    if (!partnerRow) throw new Error('partner row not found');
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create & link/i }));

    expect(await within(partnerRow as HTMLElement).findByRole('alert')).toHaveTextContent(/first and a last name/i);
  });

  it('locks the name while the create is in flight', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    createGolferAndLinkMock.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderPanel();

    const alexRow = (await screen.findByText('Alex Fitzpatrick')).closest('li');
    if (!alexRow) throw new Error('competitor row not found');
    await user.click(within(alexRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));
    await user.click(within(alexRow as HTMLElement).getByRole('button', { name: /Create & link/i }));

    expect(await within(alexRow as HTMLElement).findByLabelText(/Golfer name/i)).toBeDisabled();
  });

  it('disables Create & link while the name is blank', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    const user = userEvent.setup();
    renderPanel();

    const partnerRow = (await screen.findByText('Fitzpatrick')).closest('li');
    if (!partnerRow) throw new Error('partner row not found');
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));

    const submit = within(partnerRow as HTMLElement).getByRole('button', { name: /Create & link/i });
    const name = within(partnerRow as HTMLElement).getByLabelText(/Golfer name/i);
    await user.clear(name);
    await user.type(name, '   ');
    expect(submit).toBeDisabled();

    await user.type(name, 'Other Fitzpatrick');
    expect(submit).toBeEnabled();
  });

  it('hides the create affordance when the tournament is finalized', async () => {
    competitorsMock.mockResolvedValue(buildListing({ isFinalized: true }));
    renderPanel();
    await screen.findByRole('status');

    expect(
      screen.queryByRole('button', { name: /Create new golfer/i }),
    ).not.toBeInTheDocument();
  });

  it('cancels back to the dropdown without creating anything', async () => {
    competitorsMock.mockResolvedValue(buildListing());
    const user = userEvent.setup();
    renderPanel();

    const partnerRow = (await screen.findByText('Fitzpatrick')).closest('li');
    if (!partnerRow) throw new Error('partner row not found');
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Create new golfer/i }));
    await user.click(within(partnerRow as HTMLElement).getByRole('button', { name: /Cancel/i }));

    expect(within(partnerRow as HTMLElement).queryByLabelText(/Golfer name/i)).not.toBeInTheDocument();
    expect(createGolferAndLinkMock).not.toHaveBeenCalled();
  });
});
