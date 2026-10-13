import type { ReportSideBetRound, ReportSideBetTeamEntry, WeeklyReport } from '@/shared/api/types';

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

function toEntry(raw: ReportSideBetTeamEntry): LateRowBetEntry {
  return {
    teamId: raw.teamId,
    teamName: raw.teamName,
    golferName: raw.golferName,
    cumulativeEarnings: raw.cumulativeEarnings,
    payout: raw.payout,
  };
}

export function lateRowBetRounds(report: WeeklyReport): LateRowBetRound[] {
  return report.sideBetDetail.map((rd: ReportSideBetRound) => ({
    round: rd.round,
    entries: rd.teams
      .map(toEntry)
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
