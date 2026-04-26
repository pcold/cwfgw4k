import type {
  ReportCell,
  ReportSideBetRound,
  ReportTeamColumn,
  WeeklyReport,
} from '@/shared/api/types';

export const ROUNDS = [1, 2, 3, 4, 5, 6, 7, 8] as const;

export function teamCellsByRound(team: ReportTeamColumn): Map<number, ReportCell> {
  const byRound = new Map<number, ReportCell>();
  for (const cell of team.cells) byRound.set(cell.round, cell);
  return byRound;
}

export function signTextClass(value: number): string {
  if (value > 0) return 'text-green-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-500';
}

export function roundCellBg(hasEarnings: boolean, isSideBetWinner: boolean): string {
  if (isSideBetWinner) return 'bg-red-800';
  if (hasEarnings) return 'bg-yellow-300';
  return '';
}

export function roundTextColor(hasEarnings: boolean, isSideBetWinner: boolean): string {
  return hasEarnings && !isSideBetWinner ? 'text-black' : 'text-gray-200';
}

// Map of side-bet round -> set of team ids that won that round's side bet.
// Used by the Weekly Report grid to highlight the winning cells.
export function sideBetWinnersByRound(
  sideBetDetail: ReportSideBetRound[],
): Map<number, Set<string>> {
  const result = new Map<number, Set<string>>();
  for (const entry of sideBetDetail) {
    const winners = new Set<string>();
    for (const team of entry.teams) {
      if (team.payout > 0) winners.add(team.teamId);
    }
    result.set(entry.round, winners);
  }
  return result;
}

export interface WeeklySummary {
  totalWon: number;
  totalLost: number;
  net: number;
}

export function summarizeWeeklyReport(report: WeeklyReport): WeeklySummary {
  const totalWon = report.teams
    .filter((t) => t.totalCash > 0)
    .reduce((s, t) => s + t.totalCash, 0);
  const totalLost = report.teams
    .filter((t) => t.totalCash < 0)
    .reduce((s, t) => s + t.totalCash, 0);
  return { totalWon, totalLost, net: totalWon + totalLost };
}

export function isSeasonReport(report: WeeklyReport): boolean {
  return report.tournament.status === 'season';
}
