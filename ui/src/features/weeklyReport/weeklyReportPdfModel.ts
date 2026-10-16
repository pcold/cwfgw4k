import type { ReportCell, Season, WeeklyReport } from '@/shared/api/types';
import { MINUS_SIGN, formatDollarsAndCents, formatWholeDollars } from '@/shared/util/money';
import { isSeasonReport } from './weeklyReportModel';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

// Helvetica, a standard PDF font, has no U+2212 in its WinAnsi encoding: jsPDF writes the code point's
// raw bytes and the reader draws garbage. The en dash is in it, and is within a hair of `+`'s width.
const PDF_MINUS_SIGN = '\u2013';

/** @see formatDollarsAndCents */
export function formatPdfDollarsAndCents(value: number): string {
  return formatDollarsAndCents(value).replace(MINUS_SIGN, PDF_MINUS_SIGN);
}

/** @see formatWholeDollars */
export function formatPdfWholeDollars(value: number): string {
  return formatWholeDollars(value).replace(MINUS_SIGN, PDF_MINUS_SIGN);
}

export function formatIsoDate(iso: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return null;
  const month = MONTH_NAMES[Number(match[2]) - 1];
  const day = Number(match[3]);
  return month ? `${month} ${day}` : null;
}

export function formatDateRange(startDate: string | null, endDate: string | null): string {
  const start = startDate ? formatIsoDate(startDate) : null;
  const end = endDate ? formatIsoDate(endDate) : null;
  if (start && end) return `${start} - ${end}`;
  return start ?? end ?? '';
}

export function cellContent(cell: ReportCell | undefined, showSeasonFooter: boolean): string {
  if (!cell || !cell.golferName) return '-';
  const ownership = cell.ownershipPct < 100 ? ` (${cell.ownershipPct}%)` : '';
  const name = cell.golferName.toUpperCase() + ownership;
  const earningsLine = cell.earnings > 0
    ? `${cell.positionStr ?? ''} - ${formatPdfDollarsAndCents(cell.earnings)}`
    : '-';
  if (!showSeasonFooter) return `${name}\n${earningsLine}`;
  const cumLine = cell.seasonTopTens > 0
    ? `${formatPdfDollarsAndCents(cell.seasonEarnings)} (${cell.seasonTopTens})`
    : formatPdfDollarsAndCents(0);
  return `${name}\n${earningsLine}\n${cumLine}`;
}

export function pdfFilename(report: WeeklyReport, season: Season | null): string {
  const year = season?.seasonYear ? String(season.seasonYear) : '';
  const seasonName = (season?.name ?? '').replace(/[^A-Za-z0-9]/g, '');
  const suffix = isSeasonReport(report)
    ? 'Season'
    : report.tournament.week
      ? `Week${report.tournament.week}`
      : 'Report';
  return `${year}${seasonName}${suffix}.pdf`;
}
