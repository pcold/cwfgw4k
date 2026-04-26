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

function samplePreview(): RosterPreview {
  return {
    totalPicks: 3,
    exactMatches: 1,
    ambiguous: 1,
    noMatch: 1,
    teams: [
      {
        teamNumber: 1,
        teamName: 'BROWN',
        picks: [
          {
            round: 1,
            inputName: 'SCHEFFLER',
            ownershipPct: 100,
            matchStatus: 'exact',
            espnId: '9478',
            espnName: 'Scottie Scheffler',
            suggestions: [],
          },
          {
            round: 2,
            inputName: 'SMITH',
            ownershipPct: 100,
            matchStatus: 'ambiguous',
            espnId: null,
            espnName: null,
            suggestions: [
              { espnId: '100', name: 'Cameron Smith' },
              { espnId: '200', name: 'Jordan Smith' },
            ],
          },
          {
            round: 3,
            inputName: 'NEWGUY',
            ownershipPct: 75,
            matchStatus: 'no_match',
            espnId: null,
            espnName: null,
            suggestions: [],
          },
        ],
      },
    ],
  };
}

describe('buildConfirmTeams', () => {
  it('maps exact, ambiguous selection, and no_match picks correctly', () => {
    const preview = samplePreview();
    const selections: Record<string, string> = {
      '1|1|SCHEFFLER': '9478|Scottie Scheffler',
      '1|2|SMITH': '100|Cameron Smith',
      '1|3|NEWGUY': '',
    };
    const teams = buildConfirmTeams(preview, selections);
    expect(teams).toEqual([
      {
        teamNumber: 1,
        teamName: 'BROWN',
        picks: [
          {
            round: 1,
            playerName: 'SCHEFFLER',
            ownershipPct: 100,
            espnId: '9478',
            espnName: 'Scottie Scheffler',
          },
          {
            round: 2,
            playerName: 'SMITH',
            ownershipPct: 100,
            espnId: '100',
            espnName: 'Cameron Smith',
          },
          {
            round: 3,
            playerName: 'NEWGUY',
            ownershipPct: 75,
            espnId: null,
            espnName: null,
          },
        ],
      },
    ]);
  });

  it('treats a "none" selection as create-new', () => {
    const preview = samplePreview();
    const selections: Record<string, string> = {
      '1|1|SCHEFFLER': '9478|Scottie Scheffler',
      '1|2|SMITH': 'none',
      '1|3|NEWGUY': '',
    };
    const teams = buildConfirmTeams(preview, selections);
    expect(teams[0].picks[1]).toMatchObject({ espnId: null, espnName: null });
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
          teamId: 'tm-1',
          teamNumber: 1,
          teamName: 'BROWN',
          picks: [
            { round: 1, golferId: 'g-1', golferName: 'Scottie Scheffler', ownershipPct: 100 },
          ],
        },
      ],
    });

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await user.type(
      screen.getByLabelText(/Roster \(paste text/i),
      'TEAM 1 BROWN\n1 SCHEFFLER',
    );
    await user.click(screen.getByRole('button', { name: /Match Players with ESPN/i }));

    expect(await screen.findByText(/Exact Matches:/)).toBeInTheDocument();
    expect(screen.getByText('Scottie Scheffler')).toBeInTheDocument();

    // The ambiguous dropdown
    const dropdown = screen.getByLabelText(/ESPN match for SMITH/i);
    await user.selectOptions(dropdown, '100|Cameron Smith');

    await user.click(screen.getByRole('button', { name: /Confirm & Create Teams/i }));

    await waitFor(() => {
      expect(confirmRosterMock).toHaveBeenCalledTimes(1);
    });
    const args = confirmRosterMock.mock.calls[0][0];
    expect(args.seasonId).toBe('sn-1');
    expect(args.teams[0].picks[1]).toMatchObject({
      playerName: 'SMITH',
      espnId: '100',
      espnName: 'Cameron Smith',
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

    await user.type(screen.getByLabelText(/Roster \(paste text/i), 'TEAM 1 BROWN');
    expect(button).toBeEnabled();
  });

  it('goes back to the input step when Back is clicked', async () => {
    previewRosterMock.mockResolvedValue(samplePreview());

    const user = userEvent.setup();
    renderWithProviders(<UploadRostersSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByText('2026 Spring')).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/^Season$/), 'sn-1');
    await user.type(screen.getByLabelText(/Roster \(paste text/i), 'TEAM 1 BROWN');
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
