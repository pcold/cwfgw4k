import type {
  LiveLeaderboardEntry,
  ReportCell,
  ReportTeamColumn,
  ReportTournamentInfo,
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

export interface FieldGolfer {
  golferId: string;
  golferName: string;
}

export interface ScoreboardTeam {
  teamId: string;
  teamName: string;
  topTenEarnings: number;
  weeklyTotal: number;
  golferScores: ScoreboardGolfer[];
  fieldGolfers: FieldGolfer[];
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

type InFieldCell = ReportCell & { golferId: string; golferName: string };

function isInField(cell: ReportCell): cell is InFieldCell {
  return cell.inField && cell.golferId !== null && cell.golferName !== null;
}

function fieldGolfersFor(team: ReportTeamColumn): FieldGolfer[] {
  return team.cells.filter(isInField).map((cell) => ({ golferId: cell.golferId, golferName: cell.golferName }));
}

export function playingLabel(team: ScoreboardTeam): string {
  return `${team.fieldGolfers.length} playing`;
}

function scoreboardTeamFor(team: ReportTeamColumn): ScoreboardTeam {
  return {
    teamId: team.teamId,
    teamName: team.teamName,
    topTenEarnings: team.topTenEarnings,
    weeklyTotal: team.weeklyTotal,
    golferScores: golferScoresFor(team),
    fieldGolfers: fieldGolfersFor(team),
  };
}

export function scoreboardTeams(report: WeeklyReport): ScoreboardTeam[] {
  const teamsById = new Map(report.teams.map((team) => [team.teamId, team]));
  return report.weeklyStandings.flatMap((teamId) => {
    const team = teamsById.get(teamId);
    return team ? [scoreboardTeamFor(team)] : [];
  });
}

const PARTNER_SEPARATOR = ' / ';

export function playerNames(entry: LiveLeaderboardEntry): string {
  return entry.players.map((player) => player.name).join(PARTNER_SEPARATOR);
}

export type LeaderboardTeamChip =
  | { readonly kind: 'team'; readonly key: string; readonly teamName: string }
  | { readonly kind: 'undrafted'; readonly key: string };

export function leaderboardTeamChips(entry: LiveLeaderboardEntry): LeaderboardTeamChip[] {
  return entry.players.flatMap((player): LeaderboardTeamChip[] =>
    player.teamNames.length > 0
      ? player.teamNames.map((teamName) => ({ kind: 'team', key: `${player.name}:${teamName}`, teamName }))
      : [{ kind: 'undrafted', key: `${player.name}:undrafted` }],
  );
}

export function leaderboardTeamNames(leaderboard: readonly LiveLeaderboardEntry[]): string[] {
  const names = new Set(leaderboard.flatMap((entry) => entry.players.flatMap((player) => player.teamNames)));
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

const ROUND_SEPARATOR = ' · ';

export function leaderboardRoundLine(entry: LiveLeaderboardEntry): string | null {
  const rounds = entry.roundScores.map((strokes, index) => `R${index + 1} ${strokes}`);
  const total = entry.totalStrokes === null ? [] : [`Tot ${entry.totalStrokes}`];
  const parts = [...rounds, ...total];
  return parts.length === 0 ? null : parts.join(ROUND_SEPARATOR);
}

export type ScoreboardPhase = 'finalized' | 'preStart' | 'live';

export function scoreboardPhase(report: WeeklyReport): ScoreboardPhase {
  if (report.tournament.status === 'completed') return 'finalized';
  return report.awaitingTeeOff ? 'preStart' : 'live';
}

export function scoreboardSubtitle(tournament: ReportTournamentInfo): string {
  const payout = tournament.payoutMultiplier !== 1 ? [`${tournament.payoutMultiplier}x payouts`] : [];
  return [tournament.name, `Week ${tournament.week ?? '?'}`, ...payout].filter(Boolean).join(' · ');
}
