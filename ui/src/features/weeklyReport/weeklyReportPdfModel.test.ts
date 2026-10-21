import { describe, expect, it } from 'vitest';
import type {
  ReportCell,
  ReportScope,
  ReportTeamColumn,
  Season,
  WeeklyReport,
} from '@/shared/api/types';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import {
  cellContent,
  formatDateRange,
  formatIsoDate,
  formatPdfDollarsAndCents,
  formatPdfWholeDollars,
  pdfFilename,
} from './weeklyReportPdfModel';

const season: Season = {
  id: 'sn-1',
  leagueId: 'lg-1',
  name: 'Summer',
  seasonYear: 2026,
  seasonNumber: 1,
  tieFloor: 1,
  sideBetAmount: 15,
  maxTeams: 13,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function buildReport(overrides: {
  scope?: ReportScope;
  week?: string | null;
  name?: string | null;
  teams?: ReportTeamColumn[];
} = {}): WeeklyReport {
  const scope = overrides.scope ?? 'tournament';
  return {
    scope,
    tournament: {
      name: overrides.name ?? 'Sample Open',
      startDate: '2026-04-16',
      endDate: '2026-04-19',
      status: scope === 'season' ? null : 'completed',
      payoutMultiplier: 1,
      week: 'week' in overrides ? overrides.week ?? null : '1',
    },
    teams: overrides.teams ?? [],
    undraftedTopTens: [],
    sideBetRules: null,
    sideBetDetail: [],
    standingsOrder: [],
    live: false,
    liveEligible: false,
    awaitingTeeOff: false,
    finalizable: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
    sideBetStandings: [],
  };
}

function row(overrides: Partial<ReportCell> = {}): ReportCell {
  return {
    round: 1,
    golferName: 'Scheffler',
    golferId: 'g-1',
    positionStr: null,
    finish: null,
    earnings: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
    inField: false,
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

describe('formatPdfDollarsAndCents', () => {
  it('signs a loss with an en dash, which Helvetica can draw, in place of U+2212', () => {
    expect(formatPdfDollarsAndCents(-1.7785)).toBe('\u2013$1.78');
    expect(formatPdfDollarsAndCents(751.5715)).toBe('+$751.57');
  });
});

describe('formatPdfWholeDollars', () => {
  it('signs a loss with an en dash, which Helvetica can draw, in place of U+2212', () => {
    expect(formatPdfWholeDollars(-1082)).toBe('\u2013$1,082');
    expect(formatPdfWholeDollars(1082)).toBe('+$1,082');
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
      .toBe('MACINTYRE\nT4 - +$7.50');
  });

  it('includes a season-footer line with cumulative earnings and top-tens count', () => {
    const r = row({
      golferName: 'Gotterup',
      positionStr: '1',
      earnings: 18,
      seasonEarnings: 18,
      seasonTopTens: 1,
    });
    expect(cellContent(r, true)).toBe('GOTTERUP\n1 - +$18.00\n+$18.00 (1)');
  });

  it('renders the season-footer as "$0" when no season top-tens yet', () => {
    expect(cellContent(row({ golferName: 'Scheffler' }), true)).toBe('SCHEFFLER\n-\n$0');
  });
});

describe('pdfFilename', () => {
  it('combines year, season name, and week for a per-tournament report', () => {
    const report = buildReport({ week: '1' });
    expect(pdfFilename(report, season)).toBe('2026SummerWeek1.pdf');
  });

  it('uses "Season" as the suffix for an all-tournaments report', () => {
    const report = buildReport({ scope: 'season', week: null });
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
    const seasonReport = buildReport({ week: null, scope: 'season' });
    expect(pdfFilename(seasonReport, null)).toBe('Season.pdf');

    const tournamentReport = buildReport({ week: '4' });
    expect(pdfFilename(tournamentReport, null)).toBe('Week4.pdf');
  });
});
