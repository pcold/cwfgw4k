import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/api/types';

export interface ScoreboardGolfer {
  golferId: string | null;
  golferName: string;
  position: number | null;
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

export interface LeaderboardEntry {
  name: string;
  position: number | null;
  scoreToPar: number | null;
  rostered: boolean;
  teamName: string | null;
}

export interface Scoreboard {
  teams: ScoreboardTeam[];
  leaderboard: LeaderboardEntry[];
  totalWon: number;
  totalLost: number;
  net: number;
}

function parsePosition(positionStr: string | null): number | null {
  if (!positionStr) return null;
  const parsed = Number.parseInt(positionStr.replace('T', ''), 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseScoreToPar(raw: string | null): number | null {
  if (raw === null) return null;
  if (raw === 'E') return 0;
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function golferScoresFor(team: ReportTeamColumn): ScoreboardGolfer[] {
  return team.rows
    .filter((row: ReportRow) => row.earnings > 0 && row.golferName !== null)
    .map((row) => ({
      golferId: row.golferId,
      golferName: row.golferName ?? '',
      position: parsePosition(row.positionStr),
      payout: row.earnings,
      ownershipPct: row.ownershipPct,
    }));
}

function scoreboardTeamFor(team: ReportTeamColumn): ScoreboardTeam {
  // team.topTens holds the per-tournament top-10 earnings sum (rows.earnings).
  // team.topTenMoney is cumulative season-to-date and belongs to the weekly report view, not here.
  return {
    teamId: team.teamId,
    teamName: team.teamName,
    topTenEarnings: team.topTens,
    weeklyTotal: team.weeklyTotal,
    golferScores: golferScoresFor(team),
  };
}

function leaderboardFor(report: WeeklyReport): LeaderboardEntry[] {
  // In live preview mode the backend ships the full ESPN top-20 (rostered + undrafted) so we render it directly.
  if (report.liveLeaderboard.length > 0) {
    return report.liveLeaderboard
      .slice()
      .sort((a, b) => a.position - b.position)
      .slice(0, 20)
      .map((entry) => ({
        name: entry.name,
        position: entry.position,
        scoreToPar: parseScoreToPar(entry.scoreToPar),
        rostered: entry.rostered,
        teamName: entry.teamName,
      }));
  }

  const undrafted: LeaderboardEntry[] = report.undraftedTopTens.map((entry) => ({
    name: entry.name,
    position: entry.position,
    scoreToPar: parseScoreToPar(entry.scoreToPar),
    rostered: false,
    teamName: null,
  }));

  const rostered: LeaderboardEntry[] = [];
  for (const team of report.teams) {
    for (const row of team.rows) {
      if (row.positionStr && row.golferName) {
        rostered.push({
          name: row.golferName,
          position: parsePosition(row.positionStr),
          scoreToPar: parseScoreToPar(row.scoreToPar),
          rostered: true,
          teamName: team.teamName,
        });
      }
    }
  }

  const combined = [...undrafted, ...rostered];
  combined.sort((a, b) => (a.position ?? 99) - (b.position ?? 99));

  const seen = new Set<string>();
  const unique = combined.filter((entry) => {
    if (seen.has(entry.name)) return false;
    seen.add(entry.name);
    return true;
  });

  return unique.slice(0, 20);
}

export function deriveScoreboard(report: WeeklyReport): Scoreboard {
  const teams = report.teams
    .map(scoreboardTeamFor)
    .sort((a, b) => b.weeklyTotal - a.weeklyTotal);
  const totalWon = teams.filter((t) => t.weeklyTotal > 0).reduce((s, t) => s + t.weeklyTotal, 0);
  const totalLost = teams.filter((t) => t.weeklyTotal < 0).reduce((s, t) => s + t.weeklyTotal, 0);
  return {
    teams,
    leaderboard: leaderboardFor(report),
    totalWon,
    totalLost,
    net: totalWon + totalLost,
  };
}

export function formatScoreToPar(score: number | null): string {
  if (score === null) return '—';
  if (score === 0) return 'E';
  return score > 0 ? `+${score}` : `${score}`;
}
