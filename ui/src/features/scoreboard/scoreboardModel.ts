import type { ReportCell, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';

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
  /** R1..R4 stroke totals when ESPN ships them; empty before play. */
  roundScores: number[];
  /** Sum of [roundScores] when at least one round is in. */
  totalStrokes: number | null;
}

export interface Scoreboard {
  teams: ScoreboardTeam[];
  leaderboard: LeaderboardEntry[];
  totalWon: number;
  totalLost: number;
  net: number;
}

function golferScoresFor(team: ReportTeamColumn): ScoreboardGolfer[] {
  return team.cells
    .filter((cell: ReportCell) => cell.earnings > 0 && cell.golferName !== null)
    .map((cell) => ({
      golferId: cell.golferId,
      golferName: cell.golferName ?? '',
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

function leaderboardFor(report: WeeklyReport): LeaderboardEntry[] {
  // In live preview mode the backend ships the full ESPN field (rostered + undrafted) so we render it directly.
  if (report.liveLeaderboard.length > 0) {
    const sorted = report.liveLeaderboard
      .slice()
      .sort((a, b) => a.position - b.position)
      .map((entry) => ({
        name: entry.name,
        position: entry.position,
        scoreToPar: entry.scoreToPar,
        rostered: entry.rostered,
        teamName: entry.teamName,
        pairKey: entry.pairKey,
        roundScores: entry.roundScores,
        totalStrokes: entry.totalStrokes,
      }));
    return collapsePairs(sorted);
  }

  const undrafted: LeaderboardEntry[] = report.undraftedTopTens.map((entry) => ({
    name: entry.name,
    position: entry.position,
    scoreToPar: entry.scoreToPar,
    rostered: false,
    teamName: null,
    pairKey: entry.pairKey,
    roundScores: [],
    totalStrokes: null,
  }));

  const rostered: LeaderboardEntry[] = [];
  for (const team of report.teams) {
    for (const cell of team.cells) {
      if (cell.finish && cell.golferName) {
        rostered.push({
          name: cell.golferName,
          position: cell.finish.position,
          scoreToPar: cell.scoreToPar,
          rostered: true,
          teamName: team.teamName,
          pairKey: cell.pairKey,
          // Non-live mode doesn't carry round-by-round through ReportCell yet;
          // live mode does. Show "—" in those columns until that pipeline is added.
          roundScores: [],
          totalStrokes: null,
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

  return collapsePairs(unique);
}

// For team events (e.g. Zurich Classic) two golfers share one competitor slot, so entries with the same pairKey
// collapse into a single cell with "A/B" player names and "teamA/teamB" teams in matching order. Undrafted partners
// keep the literal "undrafted" placeholder so the joined team string stays aligned with the player string.
export function collapsePairs(entries: readonly LeaderboardEntry[]): LeaderboardEntry[] {
  const slots: CompetitorSlot[] = [];
  const slotByPairKey = new Map<string, CompetitorSlot>();
  for (const entry of entries) {
    const key = entry.pairKey;
    const slot = key ? slotByPairKey.get(key) : undefined;
    if (slot) {
      slot.push(entry);
    } else {
      const opened: CompetitorSlot = [entry];
      if (key) slotByPairKey.set(key, opened);
      slots.push(opened);
    }
  }
  return slots.map(([first, ...partners]) => partners.reduce(joinPartner, first));
}

type CompetitorSlot = [LeaderboardEntry, ...LeaderboardEntry[]];

function joinPartner(joined: LeaderboardEntry, partner: LeaderboardEntry): LeaderboardEntry {
  return {
    ...joined,
    name: `${joined.name} / ${partner.name}`,
    teamName: `${joined.teamName ?? 'undrafted'} / ${partner.teamName ?? 'undrafted'}`,
    rostered: joined.rostered || partner.rostered,
  };
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
