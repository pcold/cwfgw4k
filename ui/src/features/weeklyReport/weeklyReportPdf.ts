import jsPDF from 'jspdf';
import autoTable, { type Cell, type CellDef, type RowInput } from 'jspdf-autotable';
import type {
  ReportCell,
  ReportTeamColumn,
  Season,
  StandingsEntry,
  WeeklyReport,
} from '@/shared/api/types';
import { formatUnsignedDollarsAndCents, moneyTone, type MoneyTone } from '@/shared/util/money';
import {
  ROUNDS,
  isSeasonReport,
  sideBetAmountByRound,
  sideBetRowLabel,
  sideBetWinnersByRound,
  teamCellsByRound,
} from './weeklyReportModel';
import {
  cellContent,
  formatDateRange,
  formatPdfDollarsAndCents,
  formatPdfWholeDollars,
  pdfFilename,
} from './weeklyReportPdfModel';

type RGB = [number, number, number];

/** @see PDF_COLOURS */
export type PdfColourToken =
  | 'header-band'
  | 'on-fill'
  | 'ink'
  | 'ink-muted'
  | 'ink-soft'
  | 'hairline'
  | 'gold'
  | 'late-row-leader'
  | 'money-up-fill'
  | 'money-down-fill'
  | 'money-even-fill'
  | 'semantic-success'
  | 'semantic-danger';

/**
 * Print copies, keyed by the role token each copies: jsPDF takes RGB, and an oklch token resolves only in CSS.
 * weeklyReportPdf.test.ts pins each to its token.
 */
export const PDF_COLOURS: Readonly<Record<PdfColourToken, RGB>> = {
  'header-band': [10, 29, 56],
  'on-fill': [255, 255, 255],
  ink: [10, 29, 56],
  'ink-muted': [74, 95, 128],
  'ink-soft': [74, 95, 128],
  hairline: [213, 222, 235],
  gold: [255, 210, 63],
  'late-row-leader': [254, 249, 194],
  'money-up-fill': [15, 138, 79],
  'money-down-fill': [193, 0, 7],
  'money-even-fill': [74, 95, 128],
  'semantic-success': [1, 102, 48],
  'semantic-danger': [193, 0, 7],
};

const MONEY_TONE_FILL: Readonly<Record<MoneyTone, PdfColourToken>> = {
  up: 'money-up-fill',
  down: 'money-down-fill',
  even: 'money-even-fill',
};

/** The inks `moneyToneClass` gives the screen's Won and Lost chips; the two must change together. */
const MONEY_TONE_INK: Readonly<Record<MoneyTone, PdfColourToken>> = {
  up: 'semantic-success',
  down: 'semantic-danger',
  even: 'ink-soft',
};

const ROUND_COLUMN_WIDTH = 52;
const FOOTER_TOTALS_GAP = '    ';
const CELL_PADDING = 1.5;
const LEADER_EDGE_WIDTH = 2;
// White on the up fill is 4.40:1, which WCAG passes only as large text: bold and at least 14pt.
const TOTAL_CASH_FONT_SIZE = 14;
const STANDINGS_COLUMN_COUNT = 4;
// A full line under the heading's, at 8: beside it, the heading runs into the second column.
const STANDINGS_FIRST_LINE = 20;
const STANDINGS_LINE_PITCH = 10;

type Emphasis = 'bold' | 'normal';

function roundCell(cell: ReportCell | undefined, isLateRowLeader: boolean, showSeasonFooter: boolean): CellDef {
  const earned = !!cell && cell.earnings > 0;
  return {
    content: cellContent(cell, showSeasonFooter),
    styles: {
      fontSize: 6,
      halign: 'center',
      valign: 'top',
      // The gold edge is drawn over the cell's left side, so the text keeps clear of it.
      cellPadding: isLateRowLeader
        ? [CELL_PADDING, CELL_PADDING, CELL_PADDING, CELL_PADDING + LEADER_EDGE_WIDTH]
        : CELL_PADDING,
      fillColor: isLateRowLeader ? PDF_COLOURS['late-row-leader'] : false,
      textColor: PDF_COLOURS[earned ? 'ink' : 'ink-muted'],
      fontStyle: earned ? 'bold' : 'normal',
    },
  };
}

function cornerCell(label: string): CellDef {
  return { content: label, styles: { fillColor: false, textColor: PDF_COLOURS['ink-muted'] } };
}

function teamHeadCell(text: string): CellDef {
  return { content: text, styles: { fillColor: PDF_COLOURS['header-band'], textColor: PDF_COLOURS['on-fill'] } };
}

function summaryLabelCell(label: string, ink: PdfColourToken, emphasis: Emphasis): CellDef {
  return {
    content: label,
    styles: { textColor: PDF_COLOURS[ink], fontStyle: emphasis, halign: 'right', fontSize: 7 },
  };
}

function summaryValueCell(text: string, ink: PdfColourToken, emphasis: Emphasis): CellDef {
  return { content: text, styles: { textColor: PDF_COLOURS[ink], fontStyle: emphasis, fontSize: 7 } };
}

function subtotalCell(team: ReportTeamColumn): CellDef {
  return summaryValueCell(
    `${formatPdfDollarsAndCents(team.subtotal)}\n(${formatPdfDollarsAndCents(team.topTenMoney)})`,
    'ink',
    'bold',
  );
}

function totalCashCell(team: ReportTeamColumn): CellDef {
  const totalCash = formatPdfWholeDollars(team.totalCash);
  return {
    content: totalCash,
    styles: {
      fillColor: PDF_COLOURS[MONEY_TONE_FILL[moneyTone(totalCash)]],
      textColor: PDF_COLOURS['on-fill'],
      fontStyle: 'bold',
      fontSize: TOTAL_CASH_FONT_SIZE,
    },
  };
}

// Autotable styles a cell as a whole, so a late round's tag and stake take the number's bold ink.
function roundLabelCell(round: number, sideBetAmount: number | undefined): CellDef {
  const text = sideBetAmount === undefined
    ? `${round}`
    : `${round}\nLATE ROW\n(${formatUnsignedDollarsAndCents(sideBetAmount)})`;
  return {
    content: text,
    styles: { textColor: PDF_COLOURS.ink, fontStyle: 'bold', fontSize: 7 },
  };
}

function drawHeader(
  doc: jsPDF,
  report: WeeklyReport,
  marginLeft: number,
  marginRight: number,
  startY: number,
): number {
  const { tournament, standingsOrder } = report;
  const seasonMode = isSeasonReport(report);
  const pageWidth = doc.internal.pageSize.getWidth();
  const usableWidth = pageWidth - marginLeft - marginRight;

  doc.setFillColor(...PDF_COLOURS['header-band']);
  doc.rect(marginLeft, startY, 140, 42, 'F');
  doc.setTextColor(...PDF_COLOURS['on-fill']);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(13);
  const weekLabel = tournament.week ?? '';
  const title = seasonMode ? 'Season Totals' : weekLabel ? `Week ${weekLabel}` : '';
  doc.text(title || (tournament.name ?? 'Weekly Report'), marginLeft + 6, startY + 16);
  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  const dateLine = seasonMode ? '' : formatDateRange(tournament.startDate, tournament.endDate);
  if (dateLine) doc.text(dateLine, marginLeft + 6, startY + 32);

  const standingsWidth = 440;
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(...PDF_COLOURS.ink);
  const centerX = marginLeft + 140 + (usableWidth - 140 - standingsWidth) / 2;
  doc.text(tournament.name ?? '', centerX, startY + 16, { align: 'center' });
  if (tournament.payoutMultiplier !== 1) {
    doc.setFontSize(9);
    doc.setFont('helvetica', 'italic');
    doc.text(`${tournament.payoutMultiplier}x payouts`, centerX, startY + 30, { align: 'center' });
  }

  const standingsX = pageWidth - marginRight - standingsWidth;
  const standingsBottom = drawStandings(doc, standingsOrder, weekLabel, standingsX, startY, standingsWidth);
  return Math.max(startY + 48, standingsBottom + 6);
}

// Returns the last entry's baseline, which the header grows to clear.
function drawStandings(
  doc: jsPDF,
  standings: StandingsEntry[],
  weekLabel: string,
  x: number,
  y: number,
  width: number,
): number {
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8);
  doc.setTextColor(...PDF_COLOURS.ink);
  const heading = weekLabel ? `STANDINGS THRU WEEK ${weekLabel}` : 'STANDINGS';
  doc.text(heading, x, y + 8);

  const columns = standingsColumns(standings);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  const colWidth = width / columns.length;
  columns.forEach((col, idx) => {
    const colX = x + idx * colWidth;
    col.forEach((entry, rowIdx) => {
      const label = `${entry.rank}) ${entry.teamName.toUpperCase()}`;
      doc.text(label, colX, y + STANDINGS_FIRST_LINE + rowIdx * STANDINGS_LINE_PITCH);
    });
  });
  const rows = Math.max(1, ...columns.map((col) => col.length));
  return y + STANDINGS_FIRST_LINE + (rows - 1) * STANDINGS_LINE_PITCH;
}

// Balanced, so thirteen teams read 4 / 3 / 3 / 3 rather than 4 / 4 / 4 / 1.
function standingsColumns(standings: readonly StandingsEntry[]): StandingsEntry[][] {
  const shortLength = Math.floor(standings.length / STANDINGS_COLUMN_COUNT);
  const longColumns = standings.length % STANDINGS_COLUMN_COUNT;
  return Array.from({ length: STANDINGS_COLUMN_COUNT }, (_, column) => {
    const start = column * shortLength + Math.min(column, longColumns);
    const length = shortLength + (column < longColumns ? 1 : 0);
    return standings.slice(start, start + length);
  });
}

// jspdf-autotable records the drawn table on the doc as `lastAutoTable` but leaves it out of its typings.
function lastTableFinalY(doc: jsPDF): number {
  if ('lastAutoTable' in doc) {
    const table = doc.lastAutoTable;
    if (typeof table === 'object' && table !== null && 'finalY' in table && typeof table.finalY === 'number') {
      return table.finalY;
    }
  }
  throw new Error('jspdf-autotable recorded no finalY for the table it drew');
}

function drawFooter(
  doc: jsPDF,
  report: WeeklyReport,
  marginLeft: number,
  marginRight: number,
  y: number,
): void {
  const pageWidth = doc.internal.pageSize.getWidth();
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  doc.setTextColor(...PDF_COLOURS['ink-soft']);
  if (report.undraftedTopTens.length > 0) {
    const parts = report.undraftedTopTens.map(
      (u) => `${u.name.toUpperCase()} ${formatPdfDollarsAndCents(u.payout)}`,
    );
    doc.text(`UNDRAFTED TOP TENS: ${parts.join(', ')}`, marginLeft, y);
  }
  const won = formatPdfWholeDollars(report.cashTotals.won);
  const lost = formatPdfWholeDollars(report.cashTotals.lost);
  const right = pageWidth - marginRight;
  doc.setTextColor(...PDF_COLOURS[MONEY_TONE_INK[moneyTone(lost)]]);
  doc.text(lost, right, y, { align: 'right' });
  doc.setTextColor(...PDF_COLOURS[MONEY_TONE_INK[moneyTone(won)]]);
  doc.text(won, right - doc.getTextWidth(`${FOOTER_TOTALS_GAP}${lost}`), y, { align: 'right' });
}

export function downloadWeeklyReportPdf(report: WeeklyReport, season: Season | null): void {
  const doc = buildWeeklyReportPdf(report);
  doc.save(pdfFilename(report, season));
}

export function buildWeeklyReportPdf(report: WeeklyReport): jsPDF {
  const { teams } = report;
  const seasonMode = isSeasonReport(report);
  const showSeasonFooter = !seasonMode;
  const winnersByRound = sideBetWinnersByRound(report.sideBetDetail);
  const sideBetAmounts = sideBetAmountByRound(report.sideBetRules);

  const doc = new jsPDF({ orientation: 'landscape', unit: 'pt', format: 'letter' });
  const marginLeft = 18;
  const marginRight = 18;
  const headerBottom = drawHeader(doc, report, marginLeft, marginRight, 18);

  const teamsByRound = teams.map((t) => ({ team: t, rows: teamCellsByRound(t) }));

  const head: RowInput[] = [
    [cornerCell('TEAM #'), ...teams.map((_, i) => teamHeadCell(String(i + 1)))],
    [cornerCell('ROW #'), ...teams.map((t) => teamHeadCell(t.teamName.toUpperCase()))],
  ];

  const lateRowLeaders = new Set<Cell['raw']>();
  const body: RowInput[] = [];
  for (const round of ROUNDS) {
    const winners = winnersByRound.get(round) ?? new Set<string>();
    const cells: CellDef[] = [roundLabelCell(round, sideBetAmounts.get(round))];
    for (const { team, rows } of teamsByRound) {
      const isLateRowLeader = winners.has(team.teamId);
      const cell = roundCell(rows.get(round), isLateRowLeader, showSeasonFooter);
      if (isLateRowLeader) lateRowLeaders.add(cell);
      cells.push(cell);
    }
    body.push(cells);
  }

  body.push([
    summaryLabelCell('TOP TENS', 'ink', 'bold'),
    ...teams.map((t) => summaryValueCell(formatPdfDollarsAndCents(t.topTenEarnings), 'ink', 'bold')),
  ]);

  body.push([
    summaryLabelCell(seasonMode ? '*SEASON*' : '*WEEKLY TOTAL*', 'ink', 'bold'),
    ...teams.map((t) => summaryValueCell(formatPdfDollarsAndCents(t.weeklyTotal), 'ink', 'bold')),
  ]);

  body.push([
    summaryLabelCell('PREVIOUS', 'ink-muted', 'normal'),
    ...teams.map((t) => summaryValueCell(formatPdfDollarsAndCents(t.previous), 'ink-muted', 'normal')),
  ]);

  body.push([summaryLabelCell('SUBTOTAL', 'ink', 'bold'), ...teams.map(subtotalCell)]);

  body.push([
    summaryLabelCell(sideBetRowLabel(report.sideBetRules), 'ink', 'normal'),
    ...teams.map((t) => summaryValueCell(formatPdfDollarsAndCents(t.sideBets), 'ink', 'normal')),
  ]);

  body.push([summaryLabelCell('ONGOING\nTOTAL CASH', 'ink', 'bold'), ...teams.map(totalCashCell)]);

  // Even widths, so every TOTAL CASH box keeps room for a four-figure total at its large-text size.
  const tableWidth = doc.internal.pageSize.getWidth() - marginLeft - marginRight;
  const teamColumnWidth = (tableWidth - ROUND_COLUMN_WIDTH) / teams.length;
  autoTable(doc, {
    head,
    body,
    startY: headerBottom,
    margin: { left: marginLeft, right: marginRight, bottom: 28 },
    theme: 'grid',
    tableLineColor: PDF_COLOURS.hairline,
    tableLineWidth: 0.3,
    styles: {
      font: 'helvetica',
      fontSize: 6,
      cellPadding: CELL_PADDING,
      fillColor: false,
      textColor: PDF_COLOURS.ink,
      lineColor: PDF_COLOURS.hairline,
      lineWidth: 0.3,
      valign: 'middle',
      halign: 'center',
      overflow: 'linebreak',
    },
    headStyles: { fontStyle: 'bold', fontSize: 7 },
    columnStyles: Object.fromEntries([
      [0, { cellWidth: ROUND_COLUMN_WIDTH }],
      ...teams.map((_, i): [number, { cellWidth: number }] => [i + 1, { cellWidth: teamColumnWidth }]),
    ]),
    // Autotable strokes all four sides of a cell in one colour, so the gold edge is a strip laid over the left one.
    didDrawCell: ({ cell }) => {
      if (!lateRowLeaders.has(cell.raw)) return;
      doc.setFillColor(...PDF_COLOURS.gold);
      doc.rect(cell.x, cell.y, LEADER_EDGE_WIDTH, cell.height, 'F');
    },
  });

  drawFooter(doc, report, marginLeft, marginRight, lastTableFinalY(doc) + 16);

  return doc;
}
