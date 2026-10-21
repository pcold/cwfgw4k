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
import { captured, resolveToken } from '@/shared/test/tokenColours';
import { PDF_COLOURS, buildWeeklyReportPdf, type PdfColourToken } from './weeklyReportPdf';

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
  teams?: readonly ReportTeamColumn[];
} = {}): WeeklyReport {
  const teams: readonly ReportTeamColumn[] = overrides.teams ?? [
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
    teams: [...teams],
    undraftedTopTens: Array.from({ length: overrides.undraftedLen ?? 2 }, (_, i) => ({
      name: `Undrafted Golfer ${i}`,
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
    awaitingTeeOff: false,
    liveLeaderboard: [],
    cashTotals: makeWonLostTotals(),
    sideBetTotals: makeWonLostTotals(),
    weeklyTotals: makeWonLostTotals(),
    weeklyStandings: [],
    sideBetStandings: [],
  };
}

const unescaped = (text: string): string => text.replace(/\\([()\\])/g, '$1');

// Each string the document draws, read back from the uncompressed stream's `(text) Tj` operators.
function drawnText(doc: jsPDF): string[] {
  return doc
    .output()
    .split('\n')
    .flatMap((line) => /^\((.*)\) Tj$/.exec(line)?.slice(1) ?? [])
    .map(unescaped);
}

// jsPDF writes a fill colour to two decimals and a text colour to three, as `g` when the channels match.
function paintOf(operands: string): string {
  const channels = operands.split(' ').map((channel) => Number(channel).toFixed(2));
  return (channels.length === 1 ? [channels[0], channels[0], channels[0]] : channels).join(' ');
}

function paint(token: PdfColourToken): string {
  return PDF_COLOURS[token].map((channel) => (channel / 255).toFixed(2)).join(' ');
}

interface FilledRect {
  readonly paint: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

interface DrawnRun {
  readonly text: string;
  readonly x: number;
  readonly y: number;
  readonly ink: string;
  readonly font: string;
  readonly page: number;
  // The last rectangle filled under the run's origin before it was drawn; null on bare paper.
  readonly backdrop: string | null;
}

const contains = (rect: FilledRect, x: number, y: number): boolean =>
  x >= Math.min(rect.x, rect.x + rect.width) &&
  x <= Math.max(rect.x, rect.x + rect.width) &&
  y >= Math.min(rect.y, rect.y + rect.height) &&
  y <= Math.max(rect.y, rect.y + rect.height);

// A walk over the stream's operators: `rg`/`g` set the fill, which also inks text, `re` then `f`/`B` fills a
// rectangle, `Tf` picks a font resource, `Td` places the next `Tj`. Each page object precedes its content stream.
function drawnPage(doc: jsPDF): { readonly runs: readonly DrawnRun[]; readonly rects: readonly FilledRect[] } {
  const source = doc.output();
  const baseFontByObject = new Map(
    [...source.matchAll(/^(\d+) 0 obj\n<<\n\/Type \/Font\n\/BaseFont \/([\w-]+)$/gm)].map(
      (match) => [captured(match, 1), captured(match, 2)],
    ),
  );
  const baseFontByResource = new Map(
    [...source.matchAll(/^\/(F\d+) (\d+) 0 R$/gm)].map(
      (match) => [captured(match, 1), baseFontByObject.get(captured(match, 2))],
    ),
  );
  const runs: DrawnRun[] = [];
  const rects: FilledRect[] = [];
  let fill = '';
  let font = '';
  let page = 0;
  let origin = { x: 0, y: 0 };
  let pending: Omit<FilledRect, 'paint'> | null = null;
  for (const line of source.split('\n')) {
    if (line === '<</Type /Page') page += 1;
    const colour = /^([\d. ]+) r?g$/.exec(line)?.[1];
    if (colour !== undefined) fill = paintOf(colour);
    const resource = /^\/(F\d+) [\d.]+ Tf$/.exec(line)?.[1];
    if (resource !== undefined) font = baseFontByResource.get(resource) ?? resource;
    const position = /^(-?[\d.]+) (-?[\d.]+) Td$/.exec(line);
    if (position !== null) origin = { x: Number(position[1]), y: Number(position[2]) };
    const box = /^(-?[\d.]+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+) re$/.exec(line);
    if (box !== null) {
      pending = { x: Number(box[1]), y: Number(box[2]), width: Number(box[3]), height: Number(box[4]) };
    } else if (pending !== null && /^(f|F|B|b)\*?$/.test(line)) {
      rects.push({ paint: fill, ...pending });
      pending = null;
    }
    const text = /^\((.*)\) Tj$/.exec(line)?.[1];
    if (text !== undefined) {
      const under = rects.findLast((rect) => contains(rect, origin.x, origin.y));
      runs.push({ text: unescaped(text), ...origin, ink: fill, font, page, backdrop: under?.paint ?? null });
    }
  }
  return { runs, rects };
}

function drawnRun(doc: jsPDF, text: string): DrawnRun | undefined {
  return drawnPage(doc).runs.findLast((run) => run.text === text);
}

// The footer's zero totals print `$0` too, on bare paper.
function filledRun(doc: jsPDF, text: string): DrawnRun | undefined {
  return drawnPage(doc).runs.findLast((run) => run.text === text && run.backdrop !== null);
}

const FOOTER_CASH_TOTALS = { won: 1234, lost: -987, net: 247 };

interface DrawnFooter {
  readonly lines: readonly DrawnRun[];
  readonly won: DrawnRun;
  readonly lost: DrawnRun;
}

// The footer is the last thing drawn, so its undrafted lines run from their prefix to the end, less the totals.
function drawnFooter(doc: jsPDF): DrawnFooter {
  const { runs } = drawnPage(doc);
  const won = runs.findLast((run) => run.text === '+$1,234');
  const lost = runs.findLast((run) => run.text === '\x96$987');
  const start = runs.findIndex((run) => run.text.startsWith('UNDRAFTED TOP TENS: '));
  if (won === undefined || lost === undefined || start < 0) throw new Error('the footer drew no undrafted list or totals');
  return { lines: runs.slice(start).filter((run) => run !== won && run !== lost), won, lost };
}

function footerTextWidth(doc: jsPDF, text: string): number {
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  return doc.getTextWidth(text);
}

function undraftedText(report: WeeklyReport): string {
  const parts = report.undraftedTopTens.map((undrafted) => `${undrafted.name.toUpperCase()} +$4.00`);
  return `UNDRAFTED TOP TENS: ${parts.join(', ')}`;
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
    const drawn = drawnText(buildWeeklyReportPdf(disagreeing));
    // 0x96 is the en dash in Helvetica's WinAnsi encoding, the PDF's stand-in for U+2212.
    expect(drawn).toContain('+$9');
    expect(drawn).toContain('\x96$2');
  });

  it('titles the page in white on the header band and names the tournament in ink on bare paper', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    expect(drawnRun(doc, 'Week 1')).toMatchObject({ ink: paint('on-fill'), backdrop: paint('header-band') });
    expect(drawnRun(doc, 'April 16 - April 19')).toMatchObject({ ink: paint('on-fill'), backdrop: paint('header-band') });
    expect(drawnRun(doc, 'Masters')).toMatchObject({ ink: paint('ink'), backdrop: null });
  });

  it('inks a footer total of $0 in the soft ink, as the screen colours an even chip', () => {
    const doc = buildWeeklyReportPdf({ ...buildFixture(), cashTotals: { won: 0, lost: -2, net: -2 } });
    expect(drawnRun(doc, '$0')?.ink).toBe(paint('ink-soft'));
  });

  it('inks the footer won by its gain and lost by its loss, as the screen colours its Won and Lost chips', () => {
    const doc = buildWeeklyReportPdf({ ...buildFixture(), cashTotals: { won: 9, lost: -2, net: 7 } });
    expect(drawnRun(doc, '+$9')?.ink).toBe(paint('semantic-success'));
    expect(drawnRun(doc, '\x96$2')?.ink).toBe(paint('semantic-danger'));
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

  it('names a payout multiplier other than one, a halved week included, and omits the multiplier at exactly one', () => {
    expect(drawnText(buildWeeklyReportPdf(buildFixture({ payoutMultiplier: 0.5 })))).toContain('0.5x payouts');
    expect(drawnText(buildWeeklyReportPdf(buildFixture({ payoutMultiplier: 2 })))).toContain('2x payouts');
    const plain = drawnText(buildWeeklyReportPdf(buildFixture({ payoutMultiplier: 1 })));
    expect(plain.filter((text) => text.endsWith('x payouts'))).toEqual([]);
  });

  it('builds a PDF when there are no undrafted top-tens', () => {
    const doc = buildWeeklyReportPdf(buildFixture({ undraftedLen: 0 }));
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });

  it('prints a short undrafted list on one line at the left margin, beside the totals', () => {
    const report = { ...buildFixture(), cashTotals: FOOTER_CASH_TOTALS };
    const { lines, won, lost } = drawnFooter(buildWeeklyReportPdf(report));
    expect(lines.map(({ text, x, y, page }) => ({ text, x, y, page }))).toEqual([
      { text: undraftedText(report), x: 18, y: won.y, page: 1 },
    ]);
    expect(lost.y).toBe(won.y);
  });

  it('wraps a long undrafted list onto lines that each end a totals gap short of the won total', () => {
    const report = { ...buildFixture({ undraftedLen: 30 }), cashTotals: FOOTER_CASH_TOTALS };
    const doc = buildWeeklyReportPdf(report);
    const { lines, won } = drawnFooter(doc);
    const gap = footerTextWidth(doc, '    ');
    expect(lines.length).toBeGreaterThan(1);
    expect(lines.filter((line) => line.x + footerTextWidth(doc, line.text) > won.x - gap)).toEqual([]);
    expect(lines.filter((line) => line.x !== 18)).toEqual([]);
    expect(lines.map((line) => line.text).join(' ')).toBe(undraftedText(report));
  });

  it('keeps the won and lost totals beside the first of several undrafted lines, the rest below them', () => {
    const doc = buildWeeklyReportPdf({ ...buildFixture({ undraftedLen: 30 }), cashTotals: FOOTER_CASH_TOTALS });
    const { lines, won, lost } = drawnFooter(doc);
    const [first, ...rest] = lines;
    expect(rest.length).toBeGreaterThan(0);
    expect(won.y).toBe(first?.y);
    expect(lost.y).toBe(first?.y);
    expect(rest.filter((line) => line.y >= won.y)).toEqual([]);
  });

  it('starts a footer that would pass the bottom margin at the top of a page of its own, totals beside it', () => {
    const doc = buildWeeklyReportPdf({ ...buildFixture({ undraftedLen: 150 }), cashTotals: FOOTER_CASH_TOTALS });
    const { lines, won, lost } = drawnFooter(doc);
    const pageHeight = doc.internal.pageSize.getHeight();
    expect(drawnRun(doc, 'TEAM #')?.page).toBe(1);
    expect(new Set([...lines, won, lost].map((run) => run.page))).toEqual(new Set([2]));
    // An 8pt line whose top sits on the 18pt margin.
    expect(pageHeight - (lines[0]?.y ?? 0)).toBe(26);
    expect(won.y).toBe(lines[0]?.y);
    expect(lost.y).toBe(lines[0]?.y);
    expect(lines.filter((line) => line.y < 18)).toEqual([]);
  });

  it('copies every print colour from its role token in index.css, to within one 8-bit step a channel', () => {
    for (const [token, printed] of Object.entries(PDF_COLOURS)) {
      const resolved = resolveToken(token);
      const drift = printed.map((channel, i) => Math.abs(channel - (resolved[i] ?? Number.NaN)));
      expect({ token, drift: drift.every((step) => step <= 1) }).toEqual({ token, drift: true });
    }
  });

  it('heads each team in white on the header band, and leaves the corner unfilled in the muted ink', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    expect(drawnRun(doc, 'ACES')).toMatchObject({
      ink: paint('on-fill'),
      font: 'Helvetica-Bold',
      backdrop: paint('header-band'),
    });
    expect(drawnRun(doc, 'TEAM #')).toMatchObject({ ink: paint('ink-muted'), backdrop: null });
  });

  it('prints a pick that earned in bold ink and one that earned nothing in the regular muted ink', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    expect(drawnRun(doc, 'GOLFERT15')).toMatchObject({ ink: paint('ink'), font: 'Helvetica-Bold', backdrop: null });
    expect(drawnRun(doc, 'GOLFERT25')).toMatchObject({ ink: paint('ink-muted'), font: 'Helvetica', backdrop: null });
  });

  it('fills a late-row leader with the leader fill and edges it in gold, keeping its ink', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    expect(drawnRun(doc, 'GOLFERT12 (50%)')).toMatchObject({
      ink: paint('ink-muted'),
      backdrop: paint('late-row-leader'),
    });
    expect(drawnRun(doc, 'GOLFERT22 (50%)')?.backdrop).toBeNull();
    const goldEdges = drawnPage(doc).rects.filter((rect) => rect.paint === paint('gold'));
    expect(goldEdges.map((rect) => rect.width)).toEqual([2, 2]);
  });

  it('tags each late-row round, and only those, between its number and its stake', () => {
    const drawn = drawnText(buildWeeklyReportPdf(buildFixture()));
    const tagged = drawn.flatMap((text, i) => (text === 'LATE ROW' ? [drawn.slice(i - 1, i + 2)] : []));
    expect(tagged).toEqual([
      ['2', 'LATE ROW', '($20.00)'],
      ['3', 'LATE ROW', '($20.00)'],
    ]);
    expect(drawnText(buildWeeklyReportPdf(buildFixture({ sideBetRules: null })))).not.toContain('LATE ROW');
  });

  it('prints the rows above ONGOING TOTAL CASH on bare paper in the ink, PREVIOUS in the muted ink', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    for (const amount of ['+$7.50', '+$50.00', '+$40.00', '\x96$40.00', '\x96$20.00']) {
      expect(drawnRun(doc, amount)).toMatchObject({ ink: paint('ink'), backdrop: null });
    }
    expect(drawnRun(doc, 'PREVIOUS')).toMatchObject({ ink: paint('ink-muted'), backdrop: null });
  });

  it('fills each TOTAL CASH box by the sign it prints, under a bold white figure', () => {
    const fixture = buildFixture();
    const evenTeam: WeeklyReport = {
      ...fixture,
      teams: fixture.teams.map((t) => (t.teamId === 't3' ? { ...t, totalCash: 0.2 } : t)),
    };
    const doc = buildWeeklyReportPdf(evenTeam);
    const figure = { ink: paint('on-fill'), font: 'Helvetica-Bold' };
    expect(drawnRun(doc, '+$95')).toMatchObject({ ...figure, backdrop: paint('money-up-fill') });
    expect(drawnRun(doc, '\x96$55')).toMatchObject({ ...figure, backdrop: paint('money-down-fill') });
    expect(filledRun(doc, '$0')).toMatchObject({ ...figure, backdrop: paint('money-even-fill') });
  });

  it('tones a total cash that rounds to $0 as even, and one that rounds to a loss as down', () => {
    const fixture = buildFixture();
    const nearZero: WeeklyReport = {
      ...fixture,
      teams: fixture.teams.map((t) => ({ ...t, totalCash: t.teamId === 't1' ? -0.49 : -0.5 })),
    };
    const doc = buildWeeklyReportPdf(nearZero);
    expect(drawnRun(doc, '\x96$1')?.backdrop).toBe(paint('money-down-fill'));
    expect(filledRun(doc, '$0')?.backdrop).toBe(paint('money-even-fill'));
  });

  it('fits a four-figure TOTAL CASH on one line in each of thirteen team columns', () => {
    const teams = Array.from({ length: 13 }, (_, i) => ({
      ...buildTeam(`t${i}`, `Team${i}`),
      totalCash: i % 2 === 0 ? 9999 : -9999,
    }));
    const drawn = drawnText(buildWeeklyReportPdf(buildFixture({ teams })));
    expect(drawn.filter((text) => text === '+$9,999')).toHaveLength(7);
    expect(drawn.filter((text) => text === '\x96$9,999')).toHaveLength(6);
  });

  it('heads the standings on a line of its own, over four rank-ordered columns of 4, 3, 3 and 3 teams', () => {
    const teams = Array.from({ length: 13 }, (_, i) => buildTeam(`t${i}`, `Team${i}`));
    const { runs } = drawnPage(buildWeeklyReportPdf(buildFixture({ teams })));
    const headingLine = runs.find((run) => run.text === 'STANDINGS THRU WEEK 1')?.y;
    const tableTopLine = runs.find((run) => run.text === 'TEAM #')?.y;
    const entries = runs.filter((run) => /^\d+\) TEAM\d+$/.test(run.text));
    expect(headingLine).toBeDefined();
    expect(tableTopLine).toBeDefined();
    expect(entries.filter((entry) => entry.y >= (headingLine ?? 0))).toEqual([]);
    // At 8pt, a baseline less than one line above the table's first head row overlaps it.
    expect(entries.filter((entry) => entry.y - (tableTopLine ?? 0) < 8)).toEqual([]);
    const columnLengths = new Map<number, number>();
    for (const entry of entries) columnLengths.set(entry.x, (columnLengths.get(entry.x) ?? 0) + 1);
    expect([...columnLengths.values()]).toEqual([4, 3, 3, 3]);
    expect(entries.map((entry) => entry.text)).toEqual(teams.map((team, i) => `${i + 1}) ${team.teamName.toUpperCase()}`));
  });
});
