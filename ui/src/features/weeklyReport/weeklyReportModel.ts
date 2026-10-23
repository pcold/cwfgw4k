import type {
  ReportCell,
  ReportSideBetRound,
  ReportSideBetRules,
  ReportTeamColumn,
  WeeklyReport,
} from '@/shared/api/types';
import { formatDollarsAndCents } from '@/shared/util/money';

export const ROUNDS = [1, 2, 3, 4, 5, 6, 7, 8] as const;

export function teamCellsByRound(team: ReportTeamColumn): Map<number, ReportCell> {
  const byRound = new Map<number, ReportCell>();
  for (const cell of team.cells) byRound.set(cell.round, cell);
  return byRound;
}

// Map of side-bet round -> set of team ids that won that round's side bet.
// Used by the Weekly Report grid to highlight the winning cells.
export function sideBetWinnersByRound(sideBetDetail: readonly ReportSideBetRound[]): Map<number, Set<string>> {
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

export type CellEarnings =
  | { readonly kind: 'paid'; readonly label: string }
  | { readonly kind: 'playing'; readonly label: 'Playing' }
  | { readonly kind: 'unpaid'; readonly label: '-' };

export function cellEarnings(cell: ReportCell): CellEarnings {
  if (cell.earnings > 0) {
    return { kind: 'paid', label: `${cell.positionStr ?? ''} - ${formatDollarsAndCents(cell.earnings)}` };
  }
  return cell.inField ? { kind: 'playing', label: 'Playing' } : { kind: 'unpaid', label: '-' };
}

export function topTensLabel(count: number): string {
  return `${count} ${count === 1 ? 'top 10' : 'top 10s'}`;
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

export function reportSubtitle(report: WeeklyReport): string {
  const { name, startDate, endDate, payoutMultiplier } = report.tournament;
  const span = isSeasonReport(report) ? 'Season totals' : `${startDate ?? ''}${endDate ? ` – ${endDate}` : ''}`;
  const payout = payoutMultiplier !== 1 ? [`${payoutMultiplier}x payouts`] : [];
  return [name, span, ...payout].filter(Boolean).join(' · ');
}
