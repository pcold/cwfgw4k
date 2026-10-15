import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryKey } from '@tanstack/react-query';
import type { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { PickMatch, RosterPreview } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import OpenQuery from '@/shared/test/OpenQuery';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeSeason } from '@/shared/test/fixtures';
import UploadRostersSection, { buildConfirmTeams } from './UploadRostersSection';

const leaguesMock = vi.fn<typeof api.leagues>();
const seasonsMock = vi.fn<typeof api.seasons>();
const previewRosterMock = vi.fn<typeof api.previewRoster>();
const confirmRosterMock = vi.fn<typeof api.confirmRoster>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    previewRoster: (roster: string) => previewRosterMock(roster),
    confirmRoster: (input: Parameters<typeof api.confirmRoster>[0]) => confirmRosterMock(input),
  },
  ApiError: class ApiError extends Error {},
}));

// Each key is the one its reader holds: PlayerLinksPanel for the first two, then ScoreboardPage,
// WeeklyReportPage (and LateRowBetsPage), RankingsPage, PlayerRankingsPage.
const OPEN_WHEN_AN_UPLOAD_LANDS: [string, QueryKey][] = [
  ['golfers', queryKeys.golfers.list()],
  ['tournamentCompetitors', queryKeys.tournamentCompetitors.byTournament('tn-1')],
  ['tournamentReport', queryKeys.tournamentReport.detail('sn-1', 'tn-1', false)],
  ['report', queryKeys.report.detail('sn-1', 'tn-1', false)],
  ['rankings', queryKeys.rankings.detail('sn-1', false, 'tn-1')],
  ['playerRankings', queryKeys.playerRankings.detail('sn-1', 'tn-1', false)],
];

async function uploadRosterFile(user: ReturnType<typeof userEvent.setup>, contents: string) {
  const file = new File([contents], 'roster.tsv', { type: 'text/tab-separated-values' });
  const input = screen.getByLabelText<HTMLInputElement>(/Roster file/i);
  await user.upload(input, file);
}

function samplePreview(): RosterPreview {
  return {
    totalPicks: 3,
    matchedCount: 1,
    ambiguousCount: 1,
    unmatchedCount: 1,
    teams: [
      {
        teamNumber: 1,
        teamName: 'BROWN',
        picks: [
          {
            round: 1,
            playerName: 'Scottie Scheffler',
            ownershipPct: 100,
            match: { type: 'matched', golferId: 'g-9478', golferName: 'Scottie Scheffler' },
          },
          {
            round: 2,
            playerName: 'Cam Smith',
            ownershipPct: 100,
            match: {
              type: 'ambiguous',
              candidates: [
                { type: 'existing', golferId: 'g-100', name: 'Cameron Smith' },
                { type: 'existing', golferId: 'g-200', name: 'Jordan Smith' },
              ],
            },
          },
          {
            round: 3,
            playerName: 'Brand New',
            ownershipPct: 75,
            match: { type: 'no_match' },
          },
        ],
      },
    ],
  };
}

function espnPreview(match: PickMatch): RosterPreview {
  return {
    totalPicks: 1,
    matchedCount: match.type === 'matched_espn' || match.type === 'matched_backfill' ? 1 : 0,
    ambiguousCount: match.type === 'ambiguous' ? 1 : 0,
    unmatchedCount: 0,
    teams: [
      {
        teamNumber: 1,
        teamName: 'BROWN',
        picks: [{ round: 1, playerName: 'Scottie Scheffler', ownershipPct: 100, match }],
      },
    ],
  };
}

describe('buildConfirmTeams', () => {
  it('maps matched, ambiguous selection, and no_match picks correctly', () => {
    const preview = samplePreview();
    const selections: Record<string, string> = {
      '1|2|Cam Smith': 'existing:g-100',
      '1|3|Brand New': '',
    };
    const teams = buildConfirmTeams(preview, selections);
    expect(teams).toEqual([
      {
        teamNumber: 1,
        teamName: 'BROWN',
        picks: [
          {
            round: 1,
            ownershipPct: 100,
            assignment: { type: 'existing', golferId: 'g-9478' },
          },
          {
            round: 2,
            ownershipPct: 100,
            assignment: { type: 'existing', golferId: 'g-100' },
          },
          {
            round: 3,
            ownershipPct: 75,
            assignment: { type: 'new', name: 'Brand New' },
          },
        ],
      },
    ]);
  });

  it('treats a "none" selection as create-new with the whole input name', () => {
    const preview = samplePreview();
    const selections: Record<string, string> = {
      '1|2|Cam Smith': 'none',
      '1|3|Brand New': '',
    };
    const teams = buildConfirmTeams(preview, selections);
    expect(nth(teams ?? [], 0).picks[1]).toMatchObject({
      assignment: { type: 'new', name: 'Cam Smith' },
    });
  });

  it('sends a matched_espn pick as an espn assignment, whatever the selections hold', () => {
    const preview = espnPreview({
      type: 'matched_espn',
      espnId: 'espn-9478',
      golferName: 'Scottie Scheffler',
    });
    const teams = buildConfirmTeams(preview, {});
    expect(nth(nth(teams ?? [], 0).picks, 0).assignment).toEqual({
      type: 'espn',
      espnId: 'espn-9478',
      name: 'Scottie Scheffler',
    });
  });

  it('sends a matched_backfill pick as an espn assignment, so confirm adds the id to the existing golfer', () => {
    const preview = espnPreview({
      type: 'matched_backfill',
      espnId: 'espn-9478',
      golferName: 'Scottie Scheffler',
    });
    const teams = buildConfirmTeams(preview, {});
    expect(nth(nth(teams ?? [], 0).picks, 0).assignment).toEqual({
      type: 'espn',
      espnId: 'espn-9478',
      name: 'Scottie Scheffler',
    });
  });

  it('sends an ambiguous pick resolved to an ESPN candidate as an espn assignment', () => {
    const preview = espnPreview({
      type: 'ambiguous',
      candidates: [
        { type: 'existing', golferId: 'g-100', name: 'Scottie Scheffler' },
        { type: 'espn', espnId: 'espn-9478', name: 'Scottie Scheffler' },
      ],
    });
    const teams = buildConfirmTeams(preview, { '1|1|Scottie Scheffler': 'espn:espn-9478' });
    expect(nth(nth(teams ?? [], 0).picks, 0).assignment).toEqual({
      type: 'espn',
      espnId: 'espn-9478',
      name: 'Scottie Scheffler',
    });
  });

  it("sends a no_match pick's whole name, for the server to split", () => {
    const preview: RosterPreview = {
      totalPicks: 1,
      matchedCount: 0,
      ambiguousCount: 0,
      unmatchedCount: 1,
      teams: [
        {
          teamNumber: 1,
          teamName: 'BROWN',
          picks: [
            { round: 1, playerName: 'Byeong Hun An', ownershipPct: 100, match: { type: 'no_match' } },
          ],
        },
      ],
    };
    const teams = buildConfirmTeams(preview, {});
    expect(nth(nth(teams ?? [], 0).picks, 0).assignment).toEqual({ type: 'new', name: 'Byeong Hun An' });
  });

  it('encodes nothing while an ambiguous pick sits on the placeholder', () => {
    expect(buildConfirmTeams(samplePreview(), { '1|2|Cam Smith': '' })).toBeNull();
    expect(buildConfirmTeams(samplePreview(), {})).toBeNull();
  });
});

describe('UploadRostersSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    seasonsMock.mockReset();
    previewRosterMock.mockReset();
    confirmRosterMock.mockReset();
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    seasonsMock.mockResolvedValue([makeSeason()]);
  });

  it('runs preview, allows resolving an ambiguous pick, and confirms', async () => {
    previewRosterMock.mockResolvedValue(samplePreview());
    confirmRosterMock.mockResolvedValue({
      teamsCreated: 1,
      golfersCreated: 1,
      teams: [
        {
          id: 'tm-1',
          seasonId: 'sn-1',
          ownerName: 'BROWN',
          teamName: 'BROWN',
          teamNumber: 1,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
    });

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await uploadRosterFile(user, 'TEAM 1 BROWN\n1 SCHEFFLER');
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    expect(await screen.findByText(/Exact Matches:/)).toBeInTheDocument();
    expect(screen.getAllByText('Scottie Scheffler').length).toBeGreaterThan(0);

    const dropdown = screen.getByLabelText(/Golfer match for Cam Smith/i);
    await user.selectOptions(dropdown, 'existing:g-100');

    await user.click(screen.getByRole('button', { name: /Confirm & Create Teams/i }));

    await waitFor(() => {
      expect(confirmRosterMock).toHaveBeenCalledTimes(1);
    });
    const [args] = nth(confirmRosterMock.mock.calls, 0);
    expect(args.seasonId).toBe('sn-1');
    expect(nth(args.teams, 0).picks[1]).toMatchObject({
      round: 2,
      ownershipPct: 100,
      assignment: { type: 'existing', golferId: 'g-100' },
    });

    expect(await screen.findByText('Rosters Created')).toBeInTheDocument();
    const resultRegion = screen.getByText('Rosters Created').parentElement;
    if (!resultRegion) throw new Error('the Rosters Created heading has no region around it');
    expect(within(resultRegion).getByText(/BROWN/)).toBeInTheDocument();
  });

  it('holds confirm, saying why, until each ambiguous pick has a choice', async () => {
    previewRosterMock.mockResolvedValue(samplePreview());

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await uploadRosterFile(user, 'TEAM 1 BROWN\n1 SCHEFFLER');
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    const confirm = await screen.findByRole('button', { name: /Confirm & Create Teams/i });
    const reason = /Select a player, or None of these, for each ambiguous pick to confirm/;
    expect(confirm).toBeDisabled();
    expect(screen.getByText(reason)).toBeVisible();
    expect(confirm).toHaveAccessibleDescription(reason);

    await user.selectOptions(screen.getByLabelText(/Golfer match for Cam Smith/i), 'None of these (create new)');

    expect(confirm).toBeEnabled();
    expect(screen.queryByText(reason)).not.toBeInTheDocument();
    expect(confirm).not.toHaveAccessibleDescription();
  });

  it('offers an ESPN athlete as a new golfer and confirms the chosen one as an espn assignment', async () => {
    previewRosterMock.mockResolvedValue(
      espnPreview({
        type: 'ambiguous',
        candidates: [
          { type: 'existing', golferId: 'g-100', name: 'Scottie Scheffler' },
          { type: 'espn', espnId: 'espn-9478', name: 'Scottie Scheffler' },
        ],
      }),
    );
    confirmRosterMock.mockResolvedValue({ teamsCreated: 1, golfersCreated: 1, teams: [] });

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await uploadRosterFile(user, 'TEAM 1 BROWN\n1 SCHEFFLER');
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    const dropdown = await screen.findByLabelText(/Golfer match for Scottie Scheffler/i);
    await user.selectOptions(dropdown, 'Scottie Scheffler (new from ESPN)');
    await user.click(screen.getByRole('button', { name: /Confirm & Create Teams/i }));

    await waitFor(() => {
      expect(confirmRosterMock).toHaveBeenCalledTimes(1);
    });
    const [args] = nth(confirmRosterMock.mock.calls, 0);
    expect(nth(nth(args.teams, 0).picks, 0).assignment).toEqual({
      type: 'espn',
      espnId: 'espn-9478',
      name: 'Scottie Scheffler',
    });
  });

  it('marks a pick only ESPN names as a new golfer from ESPN', async () => {
    previewRosterMock.mockResolvedValue(
      espnPreview({ type: 'matched_espn', espnId: 'espn-9478', golferName: 'Scottie Scheffler' }),
    );

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await uploadRosterFile(user, 'TEAM 1 BROWN\n1 SCHEFFLER');
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    expect(await screen.findByText('(new from ESPN)')).toBeInTheDocument();
    expect(screen.getByLabelText('exact match')).toBeInTheDocument();
  });

  it('marks a pick an id-less golfer and ESPN share as that golfer, not a new one', async () => {
    previewRosterMock.mockResolvedValue(
      espnPreview({ type: 'matched_backfill', espnId: 'espn-9478', golferName: 'Scottie Scheffler' }),
    );

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await uploadRosterFile(user, 'TEAM 1 BROWN\n1 SCHEFFLER');
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    expect(await screen.findByText('(ESPN id added on confirm)')).toBeInTheDocument();
    expect(screen.queryByText('(new from ESPN)')).not.toBeInTheDocument();
    expect(screen.getByLabelText('exact match')).toBeInTheDocument();
  });

  it.each(OPEN_WHEN_AN_UPLOAD_LANDS)(
    'refetches an open %s query after the rosters are confirmed',
    async (_root, queryKey) => {
      const fetcher = vi.fn().mockResolvedValue({});
      previewRosterMock.mockResolvedValue(samplePreview());
      confirmRosterMock.mockResolvedValue({ teamsCreated: 1, golfersCreated: 1, teams: [] });

      const user = userEvent.setup();
      renderWithProviders(
        <>
          <UploadRostersSection />
          <OpenQuery queryKey={queryKey} fetcher={fetcher} />
        </>,
        { withLeagueSeasonProvider: false },
      );

      await waitFor(() => {
        expect(screen.getByText('2026 Spring')).toBeInTheDocument();
      });
      await uploadRosterFile(user, 'TEAM 1 BROWN\n1 SCHEFFLER');
      await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));
      const confirm = await screen.findByRole('button', { name: /Confirm & Create Teams/i });
      await user.selectOptions(screen.getByLabelText(/Golfer match for Cam Smith/i), 'existing:g-100');
      expect(fetcher).toHaveBeenCalledTimes(1);
      await user.click(confirm);

      await waitFor(() => {
        expect(fetcher).toHaveBeenCalledTimes(2);
      });
    },
  );

  it('auto-selects the first season so Match Players is enabled without manual pick', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });

    const seasonSelect = screen.getByLabelText<HTMLSelectElement>(/^Season$/);
    expect(seasonSelect.value).toBe('sn-1');

    const button = screen.getByRole('button', { name: /Match Players with ESPN/i });
    expect(button).toBeDisabled();

    await uploadRosterFile(user, 'TEAM 1 BROWN');
    await waitFor(() => expect(button).toBeEnabled());
  });

  it('goes back to the input step when Back is clicked', async () => {
    previewRosterMock.mockResolvedValue(samplePreview());

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await uploadRosterFile(user, 'TEAM 1 BROWN');
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    expect(
      await screen.findByRole('button', { name: /Confirm & Create Teams/i }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Back$/i }));

    expect(
      screen.getByRole('button', { name: /Match Players with ESPN/i }),
    ).toBeInTheDocument();
  });
});
