import { describe, expect, it } from 'vitest';
import type {
  ReportRow,
  ReportSideBetRound,
  ReportTeamColumn,
  StandingsEntry,
  WeeklyReport,
} from '@/shared/api/types';
import { buildWeeklyReportPdf } from './weeklyReportPdf';

function row(overrides: Partial<ReportRow> = {}): ReportRow {
  return {
    round: 1,
    golferName: 'Scheffler',
    golferId: 'g-1',
    positionStr: null,
    scoreToPar: null,
    earnings: 0,
    topTens: 0,
    ownershipPct: 100,
    seasonEarnings: 0,
    seasonTopTens: 0,
    pairKey: null,
    ...overrides,
  };
}

function buildTeam(teamId: string, teamName: string, earningsRound?: number): ReportTeamColumn {
  const rows = [1, 2, 3, 4, 5, 6, 7, 8].map((r) =>
    row({
      round: r,
      golferName: `Golfer${teamId}${r}`,
      golferId: `g-${teamId}-${r}`,
      positionStr: r === earningsRound ? 'T4' : null,
      earnings: r === earningsRound ? 7.5 : 0,
      ownershipPct: r === 2 ? 50 : 100,
      seasonEarnings: r === earningsRound ? 7.5 : 0,
      seasonTopTens: r === earningsRound ? 1 : 0,
    }),
  );
  return {
    teamId,
    teamName,
    ownerName: `Owner-${teamName}`,
    rows,
    topTenEarnings: earningsRound ? 7.5 : 0,
    weeklyTotal: earningsRound ? 50 : -40,
    previous: 0,
    subtotal: earningsRound ? 50 : -40,
    topTenCount: earningsRound ? 1 : 0,
    topTenMoney: earningsRound ? 7.5 : 0,
    sideBets: earningsRound ? 45 : -15,
    totalCash: earningsRound ? 95 : -55,
  };
}

function buildFixture(overrides: {
  status?: string;
  week?: string | null;
  payoutMultiplier?: number;
  undraftedLen?: number;
} = {}): WeeklyReport {
  const teams: ReportTeamColumn[] = [
    buildTeam('t1', 'Aces', 5),
    buildTeam('t2', 'Birdies'),
    buildTeam('t3', 'Eagles'),
  ];
  const standings: StandingsEntry[] = teams.map((t, i) => ({
    rank: i + 1,
    teamName: t.teamName,
    totalCash: t.totalCash,
  }));
  const sideBetDetail: ReportSideBetRound[] = [5, 6, 7, 8].map((r) => ({
    round: r,
    teams: [
      { teamId: 't1', golferName: 'GolferA', cumulativeEarnings: 7.5, payout: r === 5 ? 30 : -15 },
      { teamId: 't2', golferName: 'GolferB', cumulativeEarnings: 0, payout: -15 },
      { teamId: 't3', golferName: 'GolferC', cumulativeEarnings: 0, payout: r === 5 ? -15 : 30 },
    ],
  }));
  return {
    tournament: {
      id: 'tn-1',
      name: 'Masters',
      startDate: '2026-04-16',
      endDate: '2026-04-19',
      status: overrides.status ?? 'completed',
      payoutMultiplier: overrides.payoutMultiplier ?? 1,
      week: overrides.week === undefined ? '1' : overrides.week,
    },
    teams,
    undraftedTopTens: Array.from({ length: overrides.undraftedLen ?? 2 }, (_, i) => ({
      name: `Undrafted${i}`,
      position: 10,
      payout: 4,
      scoreToPar: null,
      pairKey: null,
    })),
    sideBetDetail,
    standingsOrder: standings,
    live: false,
    liveLeaderboard: [],
  };
}

describe('buildWeeklyReportPdf', () => {
  it('builds a non-empty PDF for a per-tournament report with earnings, side-bet winners, and undrafted top-tens', () => {
    const doc = buildWeeklyReportPdf(buildFixture());
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });

  it('builds a PDF for a season-mode report with no week label and an elevated payout multiplier', () => {
    const doc = buildWeeklyReportPdf(
      buildFixture({ status: 'season', week: null, payoutMultiplier: 2 }),
    );
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });

  it('builds a PDF when there are no undrafted top-tens', () => {
    const doc = buildWeeklyReportPdf(buildFixture({ undraftedLen: 0 }));
    const blob: Blob = doc.output('blob');
    expect(blob.size).toBeGreaterThan(100);
  });
});
