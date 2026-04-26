import type {
  ReportSideBetRound,
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  WeeklyReport,
} from '@/shared/api/types';

export interface LateRowBetEntry {
  teamId: string;
  teamName: string;
  golferName: string;
  cumulativeEarnings: number;
  payout: number;
}

export interface LateRowBetRound {
  round: number;
  entries: LateRowBetEntry[];
}

export interface LateRowBetTeamTotal {
  teamId: string;
  teamName: string;
  sideBets: number;
}

export interface LateRowBetsSummary {
  totalWon: number;
  totalLost: number;
  net: number;
}

function teamNamesById(teams: ReportTeamColumn[]): Map<string, string> {
  const byId = new Map<string, string>();
  for (const team of teams) byId.set(team.teamId, team.teamName);
  return byId;
}

function toEntry(
  raw: ReportSideBetTeamEntry,
  names: Map<string, string>,
): LateRowBetEntry {
  return {
    teamId: raw.teamId,
    teamName: names.get(raw.teamId) ?? '?',
    golferName: raw.golferName,
    cumulativeEarnings: raw.cumulativeEarnings,
    payout: raw.payout,
  };
}

export function lateRowBetRounds(report: WeeklyReport): LateRowBetRound[] {
  const names = teamNamesById(report.teams);
  return report.sideBetDetail.map((rd: ReportSideBetRound) => ({
    round: rd.round,
    entries: rd.teams
      .map((entry) => toEntry(entry, names))
      .sort((a, b) => b.cumulativeEarnings - a.cumulativeEarnings),
  }));
}

export function lateRowBetTeamTotals(report: WeeklyReport): LateRowBetTeamTotal[] {
  return report.teams
    .map((team) => ({
      teamId: team.teamId,
      teamName: team.teamName,
      sideBets: team.sideBets,
    }))
    .sort((a, b) => b.sideBets - a.sideBets);
}

export function summarizeLateRowBets(report: WeeklyReport): LateRowBetsSummary {
  const totalWon = report.teams
    .filter((t) => t.sideBets > 0)
    .reduce((sum, t) => sum + t.sideBets, 0);
  const totalLost = report.teams
    .filter((t) => t.sideBets < 0)
    .reduce((sum, t) => sum + t.sideBets, 0);
  return { totalWon, totalLost, net: totalWon + totalLost };
}
