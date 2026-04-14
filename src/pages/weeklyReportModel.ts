import type { ReportSideBetRound, WeeklyReport } from '../api/types';

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
