export interface ReportTournamentInfo {
  id: string | null;
  name: string | null;
  startDate: string | null;
  endDate: string | null;
  status: string | null;
  payoutMultiplier: number;
  week: string | null;
}

export interface ReportCell {
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
  pairKey: string | null;
}

export interface ReportTeamColumn {
  teamId: string;
  teamName: string;
  ownerName: string;
  cells: ReportCell[];
  topTenEarnings: number;
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
  pairKey: string | null;
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

export interface LiveLeaderboardEntry {
  name: string;
  position: number;
  scoreToPar: string | null;
  rostered: boolean;
  teamName: string | null;
  pairKey: string | null;
}

export interface WeeklyReport {
  tournament: ReportTournamentInfo;
  teams: ReportTeamColumn[];
  undraftedTopTens: UndraftedGolfer[];
  sideBetDetail: ReportSideBetRound[];
  standingsOrder: StandingsEntry[];
  live: boolean | null;
  liveLeaderboard: LiveLeaderboardEntry[];
}

export interface TeamRanking {
  teamId: string;
  teamName: string;
  subtotal: number;
  sideBets: number;
  totalCash: number;
  series: number[];
  liveWeekly: number | null;
}

export interface Rankings {
  teams: TeamRanking[];
  weeks: string[];
  tournamentNames: string[];
  live: boolean | null;
}

export interface RosterPick {
  round: number;
  golferName: string;
  ownershipPct: number;
  golferId: string;
}

export interface RosterTeam {
  teamId: string;
  teamName: string;
  picks: RosterPick[];
}

export interface League {
  id: string;
  name: string;
  createdAt: string;
}

export interface Tournament {
  id: string;
  pgaTournamentId: string | null;
  name: string;
  seasonId: string;
  startDate: string;
  endDate: string;
  courseName: string | null;
  status: string;
  purseAmount: number | null;
  payoutMultiplier: number;
  week: string | null;
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

export interface AuthStatus {
  authenticated: boolean;
  username: string | null;
}

export interface SeasonRules {
  payouts: number[];
  tieFloor: number;
  sideBetRounds: number[];
  sideBetAmount: number;
}

export interface SkippedSeasonImportEntry {
  espnEventId: string;
  espnEventName: string;
  reason: string;
}

export interface SeasonImportResult {
  created: Tournament[];
  skipped: SkippedSeasonImportEntry[];
}

export interface RosterMatchSuggestion {
  espnId: string;
  name: string;
}

export type RosterMatchStatus = 'exact' | 'ambiguous' | 'no_match';

export interface RosterPreviewPick {
  round: number;
  inputName: string;
  ownershipPct: number;
  matchStatus: RosterMatchStatus;
  espnId: string | null;
  espnName: string | null;
  suggestions: RosterMatchSuggestion[];
}

export interface RosterPreviewTeam {
  teamNumber: number;
  teamName: string;
  picks: RosterPreviewPick[];
}

export interface RosterPreview {
  totalPicks: number;
  exactMatches: number;
  ambiguous: number;
  noMatch: number;
  teams: RosterPreviewTeam[];
}

export interface RosterConfirmPick {
  round: number;
  playerName: string;
  ownershipPct: number;
  espnId: string | null;
  espnName: string | null;
}

export interface RosterConfirmTeamInput {
  teamNumber: number;
  teamName: string;
  picks: RosterConfirmPick[];
}

export interface RosterConfirmResultPick {
  round: number;
  golferId: string;
  golferName: string;
  ownershipPct: number;
}

export interface RosterConfirmResultTeam {
  teamId: string;
  teamNumber: number;
  teamName: string;
  picks: RosterConfirmResultPick[];
}

export interface RosterConfirmResult {
  teamsCreated: number;
  golfersCreated: number;
  teams: RosterConfirmResultTeam[];
}

export interface ActionMessageResponse {
  message: string;
}

export interface GolferHistoryEntry {
  tournament: string;
  position: number;
  earnings: number;
}

export interface GolferHistory {
  golferName: string;
  golferId: string;
  totalEarnings: number;
  topTens: number;
  results: GolferHistoryEntry[];
}
