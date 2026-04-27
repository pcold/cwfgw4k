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
  // Optional on the wire because older finalized-tournament responses may
  // pre-date the round-by-round columns. The scoreboard model coerces
  // missing values to [] / null.
  roundScores?: number[];
  totalStrokes?: number | null;
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

// Mirrors backend com.cwfgw.seasons.Season. tieFloor/sideBetAmount/maxTeams
// are part of the wire shape — keeping them in the TS type so a future
// backend change ripples back to the UI as a type error rather than a
// silent shape drift.
export interface Season {
  id: string;
  leagueId: string;
  name: string;
  seasonYear: number;
  seasonNumber: number;
  status: string;
  tieFloor: number;
  sideBetAmount: number;
  maxTeams: number;
  createdAt: string;
  updatedAt: string;
}

// Mirrors backend com.cwfgw.users.User. Returned by /auth/me (200) and
// /auth/login (200). A 401 from /auth/me means "not logged in" — handled
// in `api.authMe` by returning null.
export type UserRole = 'admin' | 'user';

export interface User {
  id: string;
  username: string;
  role: UserRole;
  createdAt: string;
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

export interface GolferCandidate {
  golferId: string;
  name: string;
}

// Mirrors the backend's sealed PickMatch hierarchy
// (com.cwfgw.admin.PickMatch). kotlinx.serialization emits a "type"
// discriminator with @SerialName values matched/ambiguous/no_match.
export type PickMatch =
  | { type: 'matched'; golferId: string; golferName: string }
  | { type: 'ambiguous'; candidates: GolferCandidate[] }
  | { type: 'no_match' };

export interface RosterPreviewPick {
  round: number;
  playerName: string;
  ownershipPct: number;
  match: PickMatch;
}

export interface RosterPreviewTeam {
  teamNumber: number;
  teamName: string;
  picks: RosterPreviewPick[];
}

export interface RosterPreview {
  totalPicks: number;
  matchedCount: number;
  ambiguousCount: number;
  unmatchedCount: number;
  teams: RosterPreviewTeam[];
}

// Mirrors the backend's sealed GolferAssignment
// (com.cwfgw.admin.GolferAssignment). For new golfers the operator must
// supply first/last; existing references the resolved golfer id.
export type GolferAssignment =
  | { type: 'existing'; golferId: string }
  | { type: 'new'; firstName: string; lastName: string };

export interface RosterConfirmPick {
  round: number;
  ownershipPct: number;
  assignment: GolferAssignment;
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

// Response of POST /api/v1/seasons/{id}/clean-results — counts of rows
// removed by the clean. Mirrors backend com.cwfgw.seasons.CleanSeasonResult.
export interface CleanSeasonResult {
  scoresDeleted: number;
  resultsDeleted: number;
  standingsDeleted: number;
  tournamentsReset: number;
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
