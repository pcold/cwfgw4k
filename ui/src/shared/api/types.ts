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

// Mirrors backend com.cwfgw.teams.Team. Returned by /admin/roster/confirm
// (inside RosterUploadResult). Note that the wire shape carries `id`, not
// `teamId`, and does NOT include the persisted picks — the confirm response
// is bare team rows. If the UI grows a need for picks-after-create, the
// backend's RosterUploadResult is the right place to extend.
export interface Team {
  id: string;
  seasonId: string;
  ownerName: string;
  teamName: string;
  teamNumber: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RosterConfirmResult {
  teamsCreated: number;
  golfersCreated: number;
  teams: Team[];
}

// Response of POST /api/v1/seasons/{id}/clean-results — counts of rows
// removed by the clean. Mirrors backend com.cwfgw.seasons.CleanSeasonResult.
export interface CleanSeasonResult {
  scoresDeleted: number;
  resultsDeleted: number;
  standingsDeleted: number;
  tournamentsReset: number;
}

// Mirrors backend com.cwfgw.golfers.CreateGolferRequest. POST /api/v1/golfers.
// `pgaPlayerId` is intentionally omitted on the create-from-link-panel path —
// these are placeholder partners (e.g., the "other" Fitzpatrick at Zurich)
// that don't have an ESPN id and shouldn't get auto-matched on future imports.
export interface CreateGolferRequest {
  firstName: string;
  lastName: string;
  pgaPlayerId?: string | null;
  country?: string | null;
  worldRanking?: number | null;
}

// Mirrors backend com.cwfgw.golfers.Golfer. Returned by GET /api/v1/golfers
// (list) and GET /api/v1/golfers/{id}.
export interface Golfer {
  id: string;
  pgaPlayerId: string | null;
  firstName: string;
  lastName: string;
  country: string | null;
  worldRanking: number | null;
  active: boolean;
  updatedAt: string;
}

// Mirrors backend com.cwfgw.tournamentLinks.TournamentPlayerOverride.
export interface TournamentPlayerOverride {
  tournamentId: string;
  espnCompetitorId: string;
  golferId: string;
}

// Mirrors backend com.cwfgw.tournamentLinks.UpsertTournamentPlayerOverrideRequest.
export interface UpsertTournamentPlayerOverrideRequest {
  espnCompetitorId: string;
  golferId: string;
}

// One ESPN competitor row for the admin link-management UI. `linkedGolfer` is
// what the override-aware matcher would return right now; `hasOverride` flags
// whether that match came from a manual pin vs auto-matching.
export interface TournamentCompetitorView {
  espnCompetitorId: string;
  name: string;
  position: number;
  isTeamPartner: boolean;
  linkedGolfer: Golfer | null;
  hasOverride: boolean;
}

// Top-level response of GET /api/v1/admin/tournaments/{id}/competitors.
// `isFinalized` is the UI's signal to render the panel read-only.
export interface TournamentCompetitorListing {
  tournamentId: string;
  isFinalized: boolean;
  competitors: TournamentCompetitorView[];
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
