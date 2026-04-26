import type { RosterTeam, WeeklyReport } from '@/shared/api/types';

export interface PlayerRankingsRow {
  key: string;
  golferId: string | null;
  name: string;
  topTens: number;
  totalEarnings: number;
  teamName: string | null;
  draftRound: number | null;
}

interface RosterInfo {
  teamName: string;
  draftRound: number;
  fullName: string;
}

function indexRosters(rosters: RosterTeam[]): Map<string, RosterInfo> {
  const byGolfer = new Map<string, RosterInfo>();
  for (const team of rosters) {
    for (const pick of team.picks) {
      byGolfer.set(pick.golferId, {
        teamName: team.teamName,
        draftRound: pick.round,
        fullName: pick.golferName,
      });
    }
  }
  return byGolfer;
}

interface DraftedAgg {
  name: string;
  topTens: number;
  totalEarnings: number;
}

function aggregateDrafted(reports: WeeklyReport[]): Map<string, DraftedAgg> {
  const byGolfer = new Map<string, DraftedAgg>();
  for (const report of reports) {
    for (const team of report.teams) {
      for (const row of team.cells) {
        if (!row.golferId || row.topTens <= 0) continue;
        const existing = byGolfer.get(row.golferId) ?? {
          name: row.golferName ?? '?',
          topTens: 0,
          totalEarnings: 0,
        };
        existing.topTens += row.topTens;
        existing.totalEarnings += row.earnings;
        byGolfer.set(row.golferId, existing);
      }
    }
  }
  return byGolfer;
}

interface UndraftedAgg {
  topTens: number;
  totalEarnings: number;
}

function aggregateUndrafted(reports: WeeklyReport[]): Map<string, UndraftedAgg> {
  const byName = new Map<string, UndraftedAgg>();
  for (const report of reports) {
    for (const golfer of report.undraftedTopTens) {
      const existing = byName.get(golfer.name) ?? { topTens: 0, totalEarnings: 0 };
      existing.topTens += 1;
      existing.totalEarnings += golfer.payout;
      byName.set(golfer.name, existing);
    }
  }
  return byName;
}

export function buildPlayerRankings(
  reports: WeeklyReport[],
  rosters: RosterTeam[],
): PlayerRankingsRow[] {
  const rosterByGolfer = indexRosters(rosters);
  const drafted = aggregateDrafted(reports);
  const undrafted = aggregateUndrafted(reports);

  const rows: PlayerRankingsRow[] = [];
  for (const [golferId, agg] of drafted) {
    const roster = rosterByGolfer.get(golferId);
    rows.push({
      key: `g:${golferId}`,
      golferId,
      name: roster?.fullName ?? agg.name,
      topTens: agg.topTens,
      totalEarnings: agg.totalEarnings,
      teamName: roster?.teamName ?? null,
      draftRound: roster?.draftRound ?? null,
    });
  }
  for (const [name, agg] of undrafted) {
    rows.push({
      key: `u:${name}`,
      golferId: null,
      name,
      topTens: agg.topTens,
      totalEarnings: agg.totalEarnings,
      teamName: null,
      draftRound: null,
    });
  }

  return rows.sort((a, b) => {
    if (b.totalEarnings !== a.totalEarnings) return b.totalEarnings - a.totalEarnings;
    if (b.topTens !== a.topTens) return b.topTens - a.topTens;
    return a.name.localeCompare(b.name);
  });
}
