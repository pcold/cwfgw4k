export interface ReportTournamentInfo {
  id: string | null;
  name: string | null;
  startDate: string | null;
  endDate: string | null;
  status: string | null;
  payoutMultiplier: number;
  week: string | null;
}

export interface ReportRow {
  round: number;
  golferName: string | null;
  golferId: string | null;
  positionStr: string | null;
  scoreToPar: string | null;
  earnings: number;
  topTens: number;
  ownershipPct: number;
  seasonEarnings: number;
  seasonTopTens: number;
}

export interface ReportTeamColumn {
  teamId: string;
  teamName: string;
  ownerName: string;
  rows: ReportRow[];
  topTens: number;
  weeklyTotal: number;
  previous: number;
  subtotal: number;
  topTenCount: number;
  topTenMoney: number;
  sideBets: number;
  totalCash: number;
}

export interface UndraftedGolfer {
  name: string;
  position: number | null;
  payout: number;
  scoreToPar: string | null;
}

export interface ReportSideBetTeamEntry {
  teamId: string;
  golferName: string;
  cumulativeEarnings: number;
  payout: number;
}

export interface ReportSideBetRound {
  round: number;
  teams: ReportSideBetTeamEntry[];
}

export interface StandingsEntry {
  rank: number;
  teamName: string;
  totalCash: number;
}

export interface WeeklyReport {
  tournament: ReportTournamentInfo;
  teams: ReportTeamColumn[];
  undraftedTopTens: UndraftedGolfer[];
  sideBetDetail: ReportSideBetRound[];
  standingsOrder: StandingsEntry[];
  live: boolean | null;
}

export interface League {
  id: string;
  name: string;
  createdAt: string;
}

export interface Season {
  id: string;
  leagueId: string;
  name: string;
  seasonYear: number;
  seasonNumber: number;
  status: string;
}
