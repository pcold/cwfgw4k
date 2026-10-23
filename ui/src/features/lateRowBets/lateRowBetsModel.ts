import type { ReportSideBetRound, ReportSideBetTeamEntry, SideBetOutcome, WeeklyReport } from '@/shared/api/types';

/** @see ReportSideBetTeamEntry */
export interface LateRowBetEntry {
  readonly teamId: string;
  readonly teamName: string;
  readonly golferName: string;
  readonly cumulativeEarnings: number;
  readonly payout: number;
  readonly outcome: SideBetOutcome | null;
}

export interface LateRowBetRound {
  readonly round: number;
  readonly entries: readonly LateRowBetEntry[];
}

export interface LateRowBetTeamTotal {
  readonly teamId: string;
  readonly teamName: string;
  readonly sideBets: number;
}

function toEntry(raw: ReportSideBetTeamEntry): LateRowBetEntry {
  return {
    teamId: raw.teamId,
    teamName: raw.teamName,
    golferName: raw.golferName,
    cumulativeEarnings: raw.cumulativeEarnings,
    payout: raw.payout,
    outcome: raw.outcome,
  };
}

export function lateRowBetRounds(report: WeeklyReport): readonly LateRowBetRound[] {
  return report.sideBetDetail.map((rd: ReportSideBetRound) => ({
    round: rd.round,
    entries: rd.teams.map(toEntry),
  }));
}

export function lateRowBetTeamTotals(report: WeeklyReport): readonly LateRowBetTeamTotal[] {
  const teamsById = new Map(report.teams.map((team) => [team.teamId, team]));
  return report.sideBetStandings.flatMap((teamId) => {
    const team = teamsById.get(teamId);
    return team ? [{ teamId: team.teamId, teamName: team.teamName, sideBets: team.sideBets }] : [];
  });
}
