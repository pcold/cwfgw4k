import type {
  ReportCell,
  ReportSideBetRound,
  ReportSideBetRules,
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
      if (team.outcome === 'won') winners.add(team.teamId);
    }
    result.set(entry.round, winners);
  }
  return result;
}

export function sideBetAmountByRound(rules: ReportSideBetRules | null): Map<number, number> {
  if (!rules) return new Map();
  return new Map(rules.rounds.map((round) => [round, rules.amountPerTeam]));
}

export function sideBetRowLabel(rules: ReportSideBetRules | null): string {
  if (!rules) return 'SIDE BETS';
  return `${rules.rounds.length === 1 ? 'ROW' : 'ROWS'} ${rules.rounds.join('-')}`;
}

export function isSeasonReport(report: WeeklyReport): boolean {
  return report.scope === 'season';
}
