import type {
  LiveLeaderboardEntry,
  ReportCell,
  ReportTeamColumn,
  WeeklyReport,
} from '@/shared/api/types';

export interface ScoreboardGolfer {
  golferId: string | null;
  golferName: string;
  position: number | null;
  tied: boolean;
  payout: number;
  ownershipPct: number;
}

export interface ScoreboardTeam {
  teamId: string;
  teamName: string;
  topTenEarnings: number;
  weeklyTotal: number;
  golferScores: ScoreboardGolfer[];
}

type PaidCell = ReportCell & { golferName: string };

// A display filter over the server's `earnings`, not a payout rule: the column lists whoever was paid.
function isPaid(cell: ReportCell): cell is PaidCell {
  return cell.earnings > 0 && cell.golferName !== null;
}

function golferScoresFor(team: ReportTeamColumn): ScoreboardGolfer[] {
  return team.cells.filter(isPaid).map((cell) => ({
    golferId: cell.golferId,
    golferName: cell.golferName,
    position: cell.finish?.position ?? null,
    tied: cell.finish?.tied ?? false,
    payout: cell.earnings,
    ownershipPct: cell.ownershipPct,
  }));
}

function scoreboardTeamFor(team: ReportTeamColumn): ScoreboardTeam {
  return {
    teamId: team.teamId,
    teamName: team.teamName,
    topTenEarnings: team.topTenEarnings,
    weeklyTotal: team.weeklyTotal,
    golferScores: golferScoresFor(team),
  };
}

export function scoreboardTeams(report: WeeklyReport): ScoreboardTeam[] {
  return report.teams.map(scoreboardTeamFor).sort((a, b) => b.weeklyTotal - a.weeklyTotal);
}

const PARTNER_SEPARATOR = ' / ';
const UNDRAFTED_LABEL = 'undrafted';

export function playerNames(entry: LiveLeaderboardEntry): string {
  return entry.players.map((player) => player.name).join(PARTNER_SEPARATOR);
}

// One label per player, in player order, so a pair's teams line up under its names.
export function teamLabel(entry: LiveLeaderboardEntry): string {
  return entry.players
    .map((player) =>
      player.teamNames.length > 0 ? player.teamNames.join(PARTNER_SEPARATOR) : UNDRAFTED_LABEL,
    )
    .join(PARTNER_SEPARATOR);
}

export function leaderboardTeamNames(leaderboard: readonly LiveLeaderboardEntry[]): string[] {
  const names = new Set(
    leaderboard.flatMap((entry) => entry.players.flatMap((player) => player.teamNames)),
  );
  return Array.from(names).sort((a, b) => a.localeCompare(b));
}

export function hasUndraftedPlayer(entry: LiveLeaderboardEntry): boolean {
  return entry.players.some((player) => player.teamNames.length === 0);
}

export function hasPlayerOn(entry: LiveLeaderboardEntry, teamName: string): boolean {
  return entry.players.some((player) => player.teamNames.includes(teamName));
}

export function formatScoreToPar(score: number | null): string {
  if (score === null) return '—';
  if (score === 0) return 'E';
  return score > 0 ? `+${score}` : `${score}`;
}
