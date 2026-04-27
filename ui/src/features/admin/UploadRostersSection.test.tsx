import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UploadRostersSection, { buildConfirmTeams } from './UploadRostersSection';
import type { RosterPreview } from '@/shared/api/types';
import { renderWithProviders } from '@/shared/test/renderWithProviders';

const leaguesMock = vi.fn();
const seasonsMock = vi.fn();
const previewRosterMock = vi.fn();
const confirmRosterMock = vi.fn();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    seasons: (id: string) => seasonsMock(id),
    previewRoster: (roster: string) => previewRosterMock(roster),
    confirmRoster: (input: unknown) => confirmRosterMock(input),
  },
  ApiError: class ApiError extends Error {},
}));

async function uploadRosterFile(user: ReturnType<typeof userEvent.setup>, contents: string) {
  const file = new File([contents], 'roster.tsv', { type: 'text/tab-separated-values' });
  const input = screen.getByLabelText(/Roster file/i) as HTMLInputElement;
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
                { golferId: 'g-100', name: 'Cameron Smith' },
                { golferId: 'g-200', name: 'Jordan Smith' },
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

describe('buildConfirmTeams', () => {
  it('maps matched, ambiguous selection, and no_match picks correctly', () => {
    const preview = samplePreview();
    const selections: Record<string, string> = {
      '1|1|Scottie Scheffler': 'g-9478',
      '1|2|Cam Smith': 'g-100',
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
            assignment: { type: 'new', firstName: 'Brand', lastName: 'New' },
          },
        ],
      },
    ]);
  });

  it('treats a "none" selection as create-new with the input name split into first/last', () => {
    const preview = samplePreview();
    const selections: Record<string, string> = {
      '1|1|Scottie Scheffler': 'g-9478',
      '1|2|Cam Smith': 'none',
      '1|3|Brand New': '',
    };
    const teams = buildConfirmTeams(preview, selections);
    expect(teams[0].picks[1]).toMatchObject({
      assignment: { type: 'new', firstName: 'Cam', lastName: 'Smith' },
    });
  });

  it('puts the whole name in firstName when the input has no space', () => {
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
            { round: 1, playerName: 'Madonna', ownershipPct: 100, match: { type: 'no_match' } },
          ],
        },
      ],
    };
    const teams = buildConfirmTeams(preview, { '1|1|Madonna': '' });
    expect(teams[0].picks[0].assignment).toEqual({
      type: 'new',
      firstName: 'Madonna',
      lastName: '',
    });
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
    seasonsMock.mockResolvedValue([
      {
        id: 'sn-1',
        leagueId: 'lg-1',
        name: 'Spring',
        seasonYear: 2026,
        seasonNumber: 1,
        status: 'active',
      },
    ]);
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
    await user.selectOptions(dropdown, 'g-100');

    await user.click(screen.getByRole('button', { name: /Confirm & Create Teams/i }));

    await waitFor(() => {
      expect(confirmRosterMock).toHaveBeenCalledTimes(1);
    });
    const args = confirmRosterMock.mock.calls[0][0];
    expect(args.seasonId).toBe('sn-1');
    expect(args.teams[0].picks[1]).toMatchObject({
      round: 2,
      ownershipPct: 100,
      assignment: { type: 'existing', golferId: 'g-100' },
    });

    expect(await screen.findByText('Rosters Created')).toBeInTheDocument();
    const resultRegion = screen.getByText('Rosters Created').parentElement!;
    expect(within(resultRegion).getByText(/BROWN/)).toBeInTheDocument();
  });

  it('auto-selects the first season so Match Players is enabled without manual pick', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });

    const seasonSelect = screen.getByLabelText(/^Season$/) as HTMLSelectElement;
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
