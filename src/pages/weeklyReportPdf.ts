import jsPDF from 'jspdf';
import autoTable, { type CellDef, type RowInput, type Styles } from 'jspdf-autotable';
import type {
  ReportRow,
  ReportTeamColumn,
  StandingsEntry,
  WeeklyReport,
} from '@/api/types';
import { formatMoney } from '@/util/money';
import {
  ROUNDS,
  isSeasonReport,
  sideBetWinnersByRound,
  summarizeWeeklyReport,
  teamRowsByRound,
} from './weeklyReportModel';

type RGB = [number, number, number];

const COLOR_HEADER_BG: RGB = [234, 179, 8];
const COLOR_HIGHLIGHT_BG: RGB = [229, 231, 235];
const COLOR_TOTAL_BG: RGB = [254, 240, 138];
const COLOR_EARNINGS_BG: RGB = [253, 224, 71];
const COLOR_SIDEBET_BG: RGB = [153, 27, 27];
const COLOR_WHITE: RGB = [255, 255, 255];
const COLOR_BLACK: RGB = [0, 0, 0];
const COLOR_MUTED: RGB = [107, 114, 128];
const COLOR_POSITIVE: RGB = [22, 101, 52];
const COLOR_NEGATIVE: RGB = [153, 27, 27];

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

function formatIsoDate(iso: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return null;
  const month = MONTH_NAMES[Number(match[2]) - 1];
  const day = Number(match[3]);
  return month ? `${month} ${day}` : null;
}

function formatDateRange(startDate: string | null, endDate: string | null): string {
  const start = startDate ? formatIsoDate(startDate) : null;
  const end = endDate ? formatIsoDate(endDate) : null;
  if (start && end) return `${start} - ${end}`;
  return start ?? end ?? '';
}

function cellContent(row: ReportRow | undefined, showSeasonFooter: boolean): string {
  if (!row || !row.golferName) return '-';
  const ownership = row.ownershipPct < 100 ? ` (${row.ownershipPct}%)` : '';
  const name = row.golferName.toUpperCase() + ownership;
  const earningsLine = row.earnings > 0
    ? `${row.positionStr ?? ''} - ${formatMoney(row.earnings)}`
    : '-';
  if (!showSeasonFooter) return `${name}\n${earningsLine}`;
  const cumLine = (row.seasonTopTens ?? 0) > 0
    ? `${formatMoney(row.seasonEarnings ?? 0)} (${row.seasonTopTens ?? 0})`
    : '$0';
  return `${name}\n${earningsLine}\n${cumLine}`;
}

function roundCell(
  row: ReportRow | undefined,
  isSideBetWinner: boolean,
  showSeasonFooter: boolean,
): CellDef {
  const hasEarnings = !!row && row.earnings > 0;
  const styles: Partial<Styles> = {
    fontSize: 6,
    halign: 'center',
    valign: 'top',
    cellPadding: 1.5,
  };
  if (isSideBetWinner) {
    styles.fillColor = COLOR_SIDEBET_BG;
    styles.textColor = COLOR_WHITE;
    styles.fontStyle = 'bold';
  } else if (hasEarnings) {
    styles.fillColor = COLOR_EARNINGS_BG;
    styles.textColor = COLOR_BLACK;
    styles.fontStyle = 'bold';
  } else {
    styles.fillColor = COLOR_WHITE;
    styles.textColor = COLOR_BLACK;
  }
  return { content: cellContent(row, showSeasonFooter), styles };
}

function signColor(value: number): RGB {
  if (value > 0) return COLOR_POSITIVE;
  if (value < 0) return COLOR_NEGATIVE;
  return COLOR_MUTED;
}

function summaryLabelCell(label: string, bg: RGB, bold: boolean): CellDef {
  return {
    content: label,
    styles: {
      fillColor: bg,
      textColor: COLOR_BLACK,
      fontStyle: bold ? 'bold' : 'normal',
      halign: 'center',
      valign: 'middle',
      fontSize: 7,
    },
  };
}

function summaryValueCell(text: string, bg: RGB, color: RGB, bold: boolean): CellDef {
  return {
    content: text,
    styles: {
      fillColor: bg,
      textColor: color,
      fontStyle: bold ? 'bold' : 'normal',
      halign: 'center',
      valign: 'middle',
      fontSize: 7,
    },
  };
}

function subtotalCell(team: ReportTeamColumn): CellDef {
  return {
    content: `${formatMoney(team.subtotal)}\n(${team.topTenCount.toFixed(2)})`,
    styles: {
      fillColor: COLOR_TOTAL_BG,
      textColor: signColor(team.subtotal),
      fontStyle: 'bold',
      halign: 'center',
      valign: 'middle',
      fontSize: 7,
    },
  };
}

function roundLabelCell(round: number, sideBetAmount: number): CellDef {
  const isSideBetRound = sideBetAmount > 0;
  const text = isSideBetRound ? `${round}\n(${formatMoney(sideBetAmount)})` : `${round}`;
  return {
    content: text,
    styles: {
      fillColor: isSideBetRound ? COLOR_SIDEBET_BG : COLOR_HEADER_BG,
      textColor: isSideBetRound ? COLOR_WHITE : COLOR_BLACK,
      fontStyle: 'bold',
      halign: 'center',
      valign: 'middle',
      fontSize: 7,
    },
  };
}

function sideBetPerTeamByRound(report: WeeklyReport): Map<number, number> {
  const amounts = new Map<number, number>();
  for (const round of report.sideBetDetail) {
    const losses = round.teams.filter((t) => t.payout < 0).map((t) => Math.abs(t.payout));
    if (losses.length > 0) amounts.set(round.round, Math.max(...losses));
  }
  return amounts;
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

  doc.setFillColor(...COLOR_HEADER_BG);
  doc.rect(marginLeft, startY, 140, 42, 'F');
  doc.setTextColor(...COLOR_BLACK);
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
  doc.setTextColor(...COLOR_BLACK);
  const centerX = marginLeft + 140 + (usableWidth - 140 - standingsWidth) / 2;
  doc.text(tournament.name ?? '', centerX, startY + 16, { align: 'center' });
  if (tournament.payoutMultiplier > 1) {
    doc.setFontSize(9);
    doc.setFont('helvetica', 'italic');
    doc.text(`${tournament.payoutMultiplier}x payouts`, centerX, startY + 30, { align: 'center' });
  }

  drawStandings(doc, standingsOrder, weekLabel, pageWidth - marginRight - standingsWidth, startY, standingsWidth);
  return startY + 48;
}

function drawStandings(
  doc: jsPDF,
  standings: StandingsEntry[],
  weekLabel: string,
  x: number,
  y: number,
  width: number,
): void {
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8);
  doc.setTextColor(...COLOR_BLACK);
  const heading = weekLabel ? `STANDINGS THRU WEEK ${weekLabel}` : 'STANDINGS';
  doc.text(heading, x, y + 8);

  const columns: StandingsEntry[][] = [
    standings.slice(0, 2),
    standings.slice(2, 6),
    standings.slice(6, 10),
    standings.slice(10, 13),
  ];
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  const colWidth = width / columns.length;
  columns.forEach((col, idx) => {
    const colX = x + idx * colWidth;
    const topOffset = idx === 0 ? 20 : 8;
    col.forEach((entry, rowIdx) => {
      const label = `${entry.rank}) ${entry.teamName.toUpperCase()}`;
      doc.text(label, colX, y + topOffset + rowIdx * 10);
    });
  });
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
  doc.setTextColor(...COLOR_BLACK);
  if (report.undraftedTopTens.length > 0) {
    const parts = report.undraftedTopTens.map(
      (u) => `${u.name.toUpperCase()} ${formatMoney(u.payout)}`,
    );
    doc.text(`UNDRAFTED TOP TENS: ${parts.join(', ')}`, marginLeft, y);
  }
  const { totalWon, totalLost } = summarizeWeeklyReport(report);
  const summary = `+${formatMoney(totalWon)}    ${formatMoney(totalLost)}`;
  doc.text(summary, pageWidth - marginRight, y, { align: 'right' });
}

export function downloadWeeklyReportPdf(report: WeeklyReport): void {
  const { teams } = report;
  const seasonMode = isSeasonReport(report);
  const showSeasonFooter = !seasonMode;
  const winnersByRound = sideBetWinnersByRound(report.sideBetDetail);
  const pots = sideBetPerTeamByRound(report);

  const doc = new jsPDF({ orientation: 'landscape', unit: 'pt', format: 'letter' });
  const marginLeft = 18;
  const marginRight = 18;
  const headerBottom = drawHeader(doc, report, marginLeft, marginRight, 18);

  const teamsByRound = teams.map((t) => ({ team: t, rows: teamRowsByRound(t) }));

  const head: RowInput[] = [
    [
      summaryLabelCell('TEAM #', COLOR_HEADER_BG, true),
      ...teams.map((_, i) =>
        summaryValueCell(String(i + 1), COLOR_HEADER_BG, COLOR_BLACK, true),
      ),
    ],
    [
      summaryLabelCell('ROW #', COLOR_HEADER_BG, true),
      ...teams.map((t) =>
        summaryValueCell(t.teamName.toUpperCase(), COLOR_HEADER_BG, COLOR_BLACK, true),
      ),
    ],
  ];

  const body: RowInput[] = [];
  for (const round of ROUNDS) {
    const winners = winnersByRound.get(round) ?? new Set<string>();
    const pot = pots.get(round) ?? 0;
    const cells: CellDef[] = [roundLabelCell(round, pot)];
    for (const { team, rows } of teamsByRound) {
      cells.push(roundCell(rows.get(round), winners.has(team.teamId), showSeasonFooter));
    }
    body.push(cells);
  }

  body.push([
    summaryLabelCell('TOP TENS', COLOR_HIGHLIGHT_BG, true),
    ...teams.map((t) =>
      summaryValueCell(
        formatMoney(t.topTenEarnings),
        COLOR_HIGHLIGHT_BG,
        t.topTenEarnings > 0 ? COLOR_POSITIVE : COLOR_MUTED,
        true,
      ),
    ),
  ]);

  body.push([
    summaryLabelCell(seasonMode ? '*SEASON*' : '*WEEKLY TOTAL*', COLOR_HIGHLIGHT_BG, true),
    ...teams.map((t) =>
      summaryValueCell(formatMoney(t.weeklyTotal), COLOR_HIGHLIGHT_BG, signColor(t.weeklyTotal), true),
    ),
  ]);

  body.push([
    summaryLabelCell('PREVIOUS', COLOR_WHITE, false),
    ...teams.map((t) =>
      summaryValueCell(formatMoney(t.previous), COLOR_WHITE, COLOR_MUTED, false),
    ),
  ]);

  body.push([
    summaryLabelCell('SUBTOTAL', COLOR_TOTAL_BG, true),
    ...teams.map(subtotalCell),
  ]);

  body.push([
    summaryLabelCell('ROWS 5-6-7-8', COLOR_WHITE, false),
    ...teams.map((t) =>
      summaryValueCell(formatMoney(t.sideBets), COLOR_WHITE, signColor(t.sideBets), false),
    ),
  ]);

  body.push([
    summaryLabelCell('ONGOING\nTOTAL CASH', COLOR_TOTAL_BG, true),
    ...teams.map((t) =>
      summaryValueCell(formatMoney(t.totalCash), COLOR_TOTAL_BG, signColor(t.totalCash), true),
    ),
  ]);

  autoTable(doc, {
    head,
    body,
    startY: headerBottom,
    margin: { left: marginLeft, right: marginRight, bottom: 28 },
    theme: 'grid',
    tableLineColor: COLOR_BLACK,
    tableLineWidth: 0.3,
    styles: {
      font: 'helvetica',
      fontSize: 6,
      cellPadding: 1.5,
      lineColor: COLOR_BLACK,
      lineWidth: 0.3,
      valign: 'middle',
      halign: 'center',
      overflow: 'linebreak',
    },
    headStyles: {
      fillColor: COLOR_HEADER_BG,
      textColor: COLOR_BLACK,
      fontStyle: 'bold',
      fontSize: 7,
    },
    columnStyles: { 0: { cellWidth: 52 } },
  });

  const finalY = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY;
  drawFooter(doc, report, marginLeft, marginRight, finalY + 16);

  const filename = pdfFilename(report);
  doc.save(filename);
}

function pdfFilename(report: WeeklyReport): string {
  const base = isSeasonReport(report)
    ? 'season-report'
    : (report.tournament.week ?? report.tournament.name ?? 'weekly-report')
        .replace(/\s+/g, '-')
        .replace(/[^A-Za-z0-9-]/g, '')
        .toLowerCase() || 'weekly-report';
  return `${base}.pdf`;
}
