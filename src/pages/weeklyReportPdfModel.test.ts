import { describe, expect, it } from 'vitest';
import type {
  ReportRow,
  ReportSideBetRound,
  ReportTeamColumn,
  Season,
  WeeklyReport,
} from '@/api/types';
import {
  cellContent,
  formatDateRange,
  formatIsoDate,
  pdfFilename,
  sideBetPerTeamByRound,
} from './weeklyReportPdfModel';

const season: Season = {
  id: 'sn-1',
  leagueId: 'lg-1',
  name: 'Summer',
  seasonYear: 2026,
  seasonNumber: 1,
  status: 'active',
};

function buildReport(overrides: {
  status?: string;
  week?: string | null;
  name?: string | null;
  sideBetDetail?: ReportSideBetRound[];
  teams?: ReportTeamColumn[];
} = {}): WeeklyReport {
  return {
    tournament: {
      id: 'tn-1',
      name: overrides.name ?? 'Sample Open',
      startDate: '2026-04-16',
      endDate: '2026-04-19',
      status: overrides.status ?? 'completed',
      payoutMultiplier: 1,
      week: 'week' in overrides ? overrides.week ?? null : '1',
    },
    teams: overrides.teams ?? [],
    undraftedTopTens: [],
    sideBetDetail: overrides.sideBetDetail ?? [],
    standingsOrder: [],
    live: false,
    liveLeaderboard: [],
  };
}

function row(overrides: Partial<ReportRow> = {}): ReportRow {
  return {
    round: 1,
    golferName: 'Scheffler',
    golferId: 'g-1',
    positionStr: null,
    scoreToPar: null,
    earnings: 0,
    topTens: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
    pairKey: null,
    ...overrides,
  };
}

describe('formatIsoDate', () => {
  it('formats a valid ISO date as "Month Day"', () => {
    expect(formatIsoDate('2026-04-19')).toBe('April 19');
    expect(formatIsoDate('2026-01-01')).toBe('January 1');
    expect(formatIsoDate('2026-12-31')).toBe('December 31');
  });

  it('returns null for invalid inputs', () => {
    expect(formatIsoDate('not-a-date')).toBeNull();
    expect(formatIsoDate('2026/04/19')).toBeNull();
    expect(formatIsoDate('')).toBeNull();
  });

  it('returns null when the month is out of range', () => {
    expect(formatIsoDate('2026-13-01')).toBeNull();
    expect(formatIsoDate('2026-00-05')).toBeNull();
  });
});

describe('formatDateRange', () => {
  it('joins start and end with a dash', () => {
    expect(formatDateRange('2026-04-16', '2026-04-19')).toBe('April 16 - April 19');
  });

  it('falls back to whichever side is present', () => {
    expect(formatDateRange('2026-04-16', null)).toBe('April 16');
    expect(formatDateRange(null, '2026-04-19')).toBe('April 19');
  });

  it('returns an empty string when both are missing or invalid', () => {
    expect(formatDateRange(null, null)).toBe('');
    expect(formatDateRange('garbage', null)).toBe('');
  });
});

describe('cellContent', () => {
  it('shows a dash for an empty row', () => {
    expect(cellContent(undefined, true)).toBe('-');
    expect(cellContent(row({ golferName: null }), true)).toBe('-');
  });

  it('uppercases the golfer name and emits a placeholder earnings line', () => {
    expect(cellContent(row({ golferName: 'Scheffler' }), false)).toBe('SCHEFFLER\n-');
  });

  it('appends an ownership suffix when less than 100%', () => {
    expect(cellContent(row({ golferName: 'Scheffler', ownershipPct: 75 }), false))
      .toBe('SCHEFFLER (75%)\n-');
  });

  it('shows the position and earnings when the golfer cashed', () => {
    expect(cellContent(row({ golferName: 'Macintyre', positionStr: 'T4', earnings: 7.5 }), false))
      .toBe('MACINTYRE\nT4 - $7.50');
  });

  it('includes a season-footer line with cumulative earnings and top-tens count', () => {
    const r = row({
      golferName: 'Gotterup',
      positionStr: '1',
      earnings: 18,
      seasonEarnings: 18,
      seasonTopTens: 1,
    });
    expect(cellContent(r, true)).toBe('GOTTERUP\n1 - $18.00\n$18.00 (1)');
  });

  it('renders the season-footer as "$0" when no season top-tens yet', () => {
    expect(cellContent(row({ golferName: 'Scheffler' }), true)).toBe('SCHEFFLER\n-\n$0');
  });
});

describe('sideBetPerTeamByRound', () => {
  it('returns the per-team side-bet amount as the largest loss in the round', () => {
    const report = buildReport({
      sideBetDetail: [
        {
          round: 5,
          teams: [
            { teamId: 't-1', golferName: 'A', cumulativeEarnings: 0, payout: 180 },
            { teamId: 't-2', golferName: 'B', cumulativeEarnings: 0, payout: -15 },
            { teamId: 't-3', golferName: 'C', cumulativeEarnings: 0, payout: -15 },
          ],
        },
      ],
    });
    expect(sideBetPerTeamByRound(report).get(5)).toBe(15);
  });

  it('omits rounds with no losers (nobody paid)', () => {
    const report = buildReport({
      sideBetDetail: [{ round: 6, teams: [] }],
    });
    expect(sideBetPerTeamByRound(report).has(6)).toBe(false);
  });

  it('handles multiple rounds independently', () => {
    const report = buildReport({
      sideBetDetail: [
        {
          round: 5,
          teams: [{ teamId: 't-1', golferName: 'A', cumulativeEarnings: 0, payout: -10 }],
        },
        {
          round: 6,
          teams: [{ teamId: 't-1', golferName: 'A', cumulativeEarnings: 0, payout: -25 }],
        },
      ],
    });
    const amounts = sideBetPerTeamByRound(report);
    expect(amounts.get(5)).toBe(10);
    expect(amounts.get(6)).toBe(25);
  });
});

describe('pdfFilename', () => {
  it('combines year, season name, and week for a per-tournament report', () => {
    const report = buildReport({ week: '1' });
    expect(pdfFilename(report, season)).toBe('2026SummerWeek1.pdf');
  });

  it('uses "Season" as the suffix for an all-tournaments report', () => {
    const report = buildReport({ status: 'season', week: null });
    expect(pdfFilename(report, season)).toBe('2026SummerSeason.pdf');
  });

  it('strips non-alphanumerics from the season name', () => {
    const s: Season = { ...season, name: 'Summer 2026 (Beta)' };
    expect(pdfFilename(buildReport({ week: '3' }), s)).toBe('2026Summer2026BetaWeek3.pdf');
  });

  it('uses "Report" when the tournament has no week label', () => {
    const report = buildReport({ week: null });
    expect(pdfFilename(report, season)).toBe('2026SummerReport.pdf');
  });

  it('omits the year and season name when no season is known', () => {
    const seasonReport = buildReport({ week: null, status: 'season' });
    expect(pdfFilename(seasonReport, null)).toBe('Season.pdf');

    const tournamentReport = buildReport({ week: '4' });
    expect(pdfFilename(tournamentReport, null)).toBe('Week4.pdf');
  });
});
