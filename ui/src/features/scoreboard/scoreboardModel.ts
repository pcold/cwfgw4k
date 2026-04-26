import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';

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

export interface LeaderboardEntry {
  name: string;
  position: number | null;
  scoreToPar: number | null;
  rostered: boolean;
  teamName: string | null;
  pairKey: string | null;
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

// Backend reports position like "T1" when the golfer is tied, "1" when solo. The T prefix is the
// only signal we get, so preserve it here — the view renders it conditionally.
function isTiedPosition(positionStr: string | null): boolean {
  return positionStr != null && positionStr.startsWith('T');
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
      tied: isTiedPosition(row.positionStr),
      payout: row.earnings,
      ownershipPct: row.ownershipPct,
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

function leaderboardFor(report: WeeklyReport): LeaderboardEntry[] {
  // In live preview mode the backend ships the full ESPN top-20 (rostered + undrafted) so we render it directly.
  if (report.liveLeaderboard.length > 0) {
    const sorted = report.liveLeaderboard
      .slice()
      .sort((a, b) => a.position - b.position)
      .map((entry) => ({
        name: entry.name,
        position: entry.position,
        scoreToPar: parseScoreToPar(entry.scoreToPar),
        rostered: entry.rostered,
        teamName: entry.teamName,
        pairKey: entry.pairKey,
      }));
    return collapsePairs(sorted).slice(0, 20);
  }

  const undrafted: LeaderboardEntry[] = report.undraftedTopTens.map((entry) => ({
    name: entry.name,
    position: entry.position,
    scoreToPar: parseScoreToPar(entry.scoreToPar),
    rostered: false,
    teamName: null,
    pairKey: entry.pairKey,
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
          pairKey: row.pairKey,
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

  return collapsePairs(unique).slice(0, 20);
}

// For team events (e.g. Zurich Classic) two golfers share one competitor slot, so entries with the same pairKey
// collapse into a single row with "A/B" player names and "teamA/teamB" teams in matching order. Undrafted partners
// keep the literal "undrafted" placeholder so the joined team string stays aligned with the player string.
export function collapsePairs(entries: LeaderboardEntry[]): LeaderboardEntry[] {
  const result: LeaderboardEntry[] = [];
  const pairIndex = new Map<string, number>();
  for (const entry of entries) {
    const key = entry.pairKey;
    const existingIndex = key === null ? undefined : pairIndex.get(key);
    if (existingIndex !== undefined) {
      const existing = result[existingIndex];
      result[existingIndex] = {
        ...existing,
        name: `${existing.name} / ${entry.name}`,
        teamName: `${existing.teamName ?? 'undrafted'} / ${entry.teamName ?? 'undrafted'}`,
        rostered: existing.rostered || entry.rostered,
      };
    } else {
      if (key) pairIndex.set(key, result.length);
      result.push(entry);
    }
  }
  return result;
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
