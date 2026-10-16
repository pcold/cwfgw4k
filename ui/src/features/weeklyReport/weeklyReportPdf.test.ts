import { describe, expect, it } from 'vitest';
import type jsPDF from 'jspdf';
import type {
  ReportCell,
  ReportScope,
  ReportSideBetRound,
  ReportSideBetRules,
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  SideBetOutcome,
  StandingsEntry,
  WeeklyReport,
} from '@/shared/api/types';
import { makeWonLostTotals } from '@/shared/test/fixtures';
import { buildWeeklyReportPdf } from './weeklyReportPdf';

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
    ...overrides,
  };
}

function buildTeam(teamId: string, teamName: string, earningsRound?: number): ReportTeamColumn {
  const cells = [1, 2, 3, 4, 5, 6, 7, 8].map((r) =>
    row({
      round: r,
      golferName: `Golfer${teamId}${r}`,
      golferId: `g-${teamId}-${r}`,
      positionStr: r === earningsRound ? 'T4' : null,
      earnings: r === earningsRound ? 7.5 : 0,
      ownershipPct: r === 2 ? 50 : 100,
      seasonEarnings: r === earningsRound ? 7.5 : 0,
      seasonTopTens: r === earningsRound ? 1 : 0,
    }),
  );
  return {
    teamId,
    teamName,
    cells,
    topTenEarnings: earningsRound ? 7.5 : 0,
    weeklyTotal: earningsRound ? 50 : -40,
    previous: 0,
    subtotal: earningsRound ? 50 : -40,
    topTenCount: earningsRound ? 1 : 0,
    topTenMoney: earningsRound ? 7.5 : 0,
    sideBets: earningsRound ? 40 : -20,
    totalCash: earningsRound ? 95 : -55,
  };
}

const SIDE_BET_RULES: ReportSideBetRules = { amountPerTeam: 20, rounds: [2, 3] };

function sideBetEntry(
  teamId: string,
  teamName: string,
  payout: number,
  outcome: SideBetOutcome,
): ReportSideBetTeamEntry {
  return { teamId, teamName, golferName: `Golfer-${teamName}`, cumulativeEarnings: 0, payout, outcome };
}

function buildFixture(overrides: {
  scope?: ReportScope;
  week?: string | null;
  payoutMultiplier?: number;
  undraftedLen?: number;
  sideBetRules?: ReportSideBetRules | null;
} = {}): WeeklyReport {
  const teams: ReportTeamColumn[] = [
    buildTeam('t1', 'Aces', 5),
    buildTeam('t2', 'Birdies'),
    buildTeam('t3', 'Eagles'),
  ];
  const standings: StandingsEntry[] = teams.map((t, i) => ({
    rank: i + 1,
    teamName: t.teamName,
    totalCash: t.totalCash,
  }));
  const sideBetRules = overrides.sideBetRules === undefined ? SIDE_BET_RULES : overrides.sideBetRules;
  const sideBetDetail: ReportSideBetRound[] = (sideBetRules?.rounds ?? []).map((r) => ({
    round: r,
    teams: [
      sideBetEntry('t1', 'Aces', r === 2 ? 40 : -20, r === 2 ? 'won' : 'lost'),
      sideBetEntry('t2', 'Birdies', -20, 'lost'),
      sideBetEntry('t3', 'Eagles', r === 2 ? -20 : 40, r === 2 ? 'lost' : 'won'),
    ],
  }));
  const scope = overrides.scope ?? 'tournament';
  return {
    scope,
    tournament: {
      name: 'Masters',
      startDate: '2026-04-16',
      endDate: '2026-04-19',
      status: scope === 'season' ? null : 'completed',
      payoutMultiplier: overrides.payoutMultiplier ?? 1,
      week: overrides.week === undefined ? '1' : overrides.week,
    },
    teams,
    undraftedTopTens: Array.from({ length: overrides.undraftedLen ?? 2 }, (_, i) => ({
      name: `Undrafted${i}`,
      position: 10,
      payout: 4,
      scoreToPar: null,
      pairKey: null,
    })),
    sideBetRules,
    sideBetDetail,
    standingsOrder: standings,
    live: false,
    liveEligible: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
  };
}

// Each string the document draws, read back from the uncompressed stream's `(text) Tj` operators.
function drawnText(doc: jsPDF): string[] {
  return doc
    .output()
    .split('\n')
    .flatMap((line) => /^\((.*)\) Tj$/.exec(line)?.slice(1) ?? [])
    .map((text) => text.replace(/\\([()\\])/g, '$1'));
}

// The fill colour each string is drawn in: the operands of the last `g` or `rg` before its `Tj`.
function drawnInk(doc: jsPDF): Map<string, string> {
  const ink = new Map<string, string>();
  let fill = '';
  for (const line of doc.output().split('\n')) {
    const colour = /^([\d. ]+) r?g$/.exec(line)?.[1];
    if (colour !== undefined) fill = colour;
    const text = /^\((.*)\) Tj$/.exec(line)?.[1];
    if (text !== undefined) ink.set(text, fill);
  }
  return ink;
}

describe('buildWeeklyReportPdf', () => {
  it('builds a non-empty PDF for a per-tournament report with earnings, side-bet winners, and undrafted top-tens', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });

  it('builds a PDF for a season-mode report with no week label and an elevated payout multiplier', () => {
    const doc = buildWeeklyReportPdf(
      buildFixture({ scope: 'season', week: null, payoutMultiplier: 2 }),
    );
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });

  it("prints the season's own side-bet amount beside its rounds and names them in the totals row", () => {
    const drawn = drawnText(buildWeeklyReportPdf(buildFixture()));
    expect(drawn.filter((text) => text === '($20.00)')).toHaveLength(SIDE_BET_RULES.rounds.length);
    expect(drawn).toContain('ROWS 2-3');
  });

  it('prints no amount beside any round and a totals row naming none for a season without side bets', () => {
    const drawn = drawnText(buildWeeklyReportPdf(buildFixture({ sideBetRules: null })));
    const subtotalTopTenMoney = ['(+$7.50)', '($0)', '($0)'];
    expect(drawn.filter((text) => /^\(\+?\$/.test(text))).toEqual(subtotalTopTenMoney);
    expect(drawn).toContain('SIDE BETS');
  });

  it("prints the report's cash totals in the footer, which are not a sum over its teams", () => {
    const disagreeing: WeeklyReport = {
      ...buildFixture(),
      cashTotals: { won: 9, lost: -2, net: 7 },
      sideBetTotals: { won: 55, lost: -66, net: -11 },
    };
    // 0x96 is the en dash in Helvetica's WinAnsi encoding, the PDF's stand-in for U+2212.
    expect(drawnText(buildWeeklyReportPdf(disagreeing))).toContain('+$9    \x96$2');
  });

  it('prints the ONGOING TOTAL CASH row in whole dollars and the subtotal in cents', () => {
    const fixture = buildFixture();
    const fractional: WeeklyReport = {
      ...fixture,
      teams: fixture.teams.map((t) => (t.teamId === 't1' ? { ...t, subtotal: 751.5715, totalCash: -1082.4 } : t)),
    };
    const drawn = drawnText(buildWeeklyReportPdf(fractional));
    expect(drawn).toContain('+$751.57');
    expect(drawn).toContain('\x96$1,082');
  });

  it('builds a PDF when there are no undrafted top-tens', () => {
    const doc = buildWeeklyReportPdf(buildFixture({ undraftedLen: 0 }));
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });

  it('colours only ONGOING TOTAL CASH by sign, and draws the rows above it in the label ink', () => {
    const ink = drawnInk(buildWeeklyReportPdf(buildFixture()));
    const label = ink.get('TOTAL CASH');
    expect(ink.get('+$95')).not.toBe(label);
    expect(ink.get('\x96$55')).not.toBe(label);
    expect(ink.get('+$95')).not.toBe(ink.get('\x96$55'));
    for (const amount of ['+$7.50', '+$50.00', '+$40.00', '\x96$40.00', '\x96$20.00']) {
      expect(ink.get(amount)).toBe(label);
    }
  });

  it('tones a total cash that rounds to $0 as even, and one that rounds to a loss as down', () => {
    const fixture = buildFixture();
    const nearZero: WeeklyReport = {
      ...fixture,
      teams: fixture.teams.map((t) => ({ ...t, totalCash: t.teamId === 't1' ? -0.49 : -0.5 })),
    };
    const ink = drawnInk(buildWeeklyReportPdf(nearZero));
    const loss = drawnInk(buildWeeklyReportPdf(fixture)).get('\x96$55');
    expect(ink.get('\x96$1')).toBe(loss);
    expect(ink.get('$0')).not.toBe(loss);
    expect(ink.get('$0')).not.toBe(ink.get('TOTAL CASH'));
  });
});
