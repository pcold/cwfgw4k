import type { ReportCell, Season, WeeklyReport } from '@/shared/api/types';
import { formatMoney } from '@/shared/util/money';
import { isSeasonReport } from './weeklyReportModel';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

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
    ? `${cell.positionStr ?? ''} - ${formatMoney(cell.earnings)}`
    : '-';
  if (!showSeasonFooter) return `${name}\n${earningsLine}`;
  const cumLine = (cell.seasonTopTens ?? 0) > 0
    ? `${formatMoney(cell.seasonEarnings ?? 0)} (${cell.seasonTopTens ?? 0})`
    : '$0';
  return `${name}\n${earningsLine}\n${cumLine}`;
}

export function sideBetPerTeamByRound(report: WeeklyReport): Map<number, number> {
  const amounts = new Map<number, number>();
  for (const round of report.sideBetDetail) {
    const losses = round.teams.filter((t) => t.payout < 0).map((t) => Math.abs(t.payout));
    if (losses.length > 0) amounts.set(round.round, Math.max(...losses));
  }
  return amounts;
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
