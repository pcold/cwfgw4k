export interface ReportTournamentInfo {
  name: string | null;
  startDate: string | null;
  endDate: string | null;
  status: string | null;
  payoutMultiplier: number;
  week: string | null;
}

export interface ReportCellFinish {
  position: number;
  tied: boolean;
}

export interface ReportCell {
  round: number;
  golferName: string | null;
  golferId: string | null;
  positionStr: string | null;
  /**
   * Set exactly where `positionStr` is a finish label, which a season cell's
   * top-ten count is not. Read position and tie state here; never parse `positionStr`.
   */
  finish: ReportCellFinish | null;
  earnings: number;
  ownershipPct: number;
  seasonEarnings: number;
  seasonTopTens: number;
}

export interface ReportTeamColumn {
  teamId: string;
  teamName: string;
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
  payout: number;
}

export type SideBetOutcome = 'won' | 'lost';

/**
 * `outcome` is the server's decision and null until the round has settled
 * something for the team. Never read it off `payout`: every team wins a round
 * they all tie, at a payout of zero.
 */
export interface ReportSideBetTeamEntry {
  teamId: string;
  teamName: string;
  golferName: string;
  cumulativeEarnings: number;
  payout: number;
  outcome: SideBetOutcome | null;
}

export interface ReportSideBetRound {
  round: number;
  teams: ReportSideBetTeamEntry[];
}

/** `rounds` are the draft rounds `WeeklyReport.sideBetDetail` stacks, in the same order. */
export interface ReportSideBetRules {
  amountPerTeam: number;
  rounds: number[];
}

export interface StandingsEntry {
  rank: number;
  teamName: string;
}

/** `teamNames` lists every team rostering the player, since a golfer can be split across teams; empty is undrafted. */
export interface LeaderboardPlayer {
  name: string;
  teamNames: string[];
}

/**
 * One competitor slot, not one golfer: a team event's partners arrive as a
 * single row of two `players`. Arrives sorted by position; never re-sort it.
 */
export interface LiveLeaderboardEntry {
  position: number;
  scoreToPar: number | null;
  players: LeaderboardPlayer[];
  roundScores: number[];
  totalStrokes: number | null;
  rostered: boolean;
}

/**
 * Set by the server on every report. Never infer it from `tournament`: its
 * `status` is null on a season report, and no status value means "season".
 */
export type ReportScope = 'season' | 'tournament';

/** `lost` is a sum of losses, so zero or negative, and `net` is `won` plus it. */
export interface WonLostTotals {
  won: number;
  lost: number;
  net: number;
}

export interface WeeklyReport {
  scope: ReportScope;
  tournament: ReportTournamentInfo;
  teams: ReportTeamColumn[];
  undraftedTopTens: UndraftedGolfer[];
  /** Null for a season that stakes nothing on side bets; `sideBetDetail` is then empty. */
  sideBetRules: ReportSideBetRules | null;
  sideBetDetail: ReportSideBetRound[];
  standingsOrder: StandingsEntry[];
  live: boolean | null;
  liveLeaderboard: LiveLeaderboardEntry[];
  /** @see Rankings.liveEligible */
  liveEligible: boolean;
  /** Over every team's `totalCash`. Display it; never re-sum `totalCash`. */
  cashTotals: WonLostTotals;
  /** Over every team's `sideBets`. @see WeeklyReport.cashTotals */
  sideBetTotals: WonLostTotals;
  /** Over every team's `weeklyTotal`. @see WeeklyReport.cashTotals */
  weeklyTotals: WonLostTotals;
  /** Every `teams` id, best `weeklyTotal` first; a tie keeps `teams`' order. Lay rows out in it; never re-sort. */
  weeklyStandings: string[];
}

export interface TeamRanking {
  teamId: string;
  teamName: string;
  subtotal: number;
  sideBets: number;
  totalCash: number;
  series: number[];
}

export interface Rankings {
  teams: TeamRanking[];
  weeks: string[];
  tournamentNames: string[];
  live: boolean | null;
  /** Whether a `live` request for this selection would overlay ESPN. Show the toggle on it; never re-derive it. */
  liveEligible: boolean;
}

export interface PlayerRankingsRow {
  key: string;
  golferId: string | null;
  name: string;
  topTens: number;
  totalEarnings: number;
  teamName: string | null;
  draftRound: number | null;
}

export interface PlayerRankings {
  players: PlayerRankingsRow[];
  live: boolean;
  /** @see Rankings.liveEligible */
  liveEligible: boolean;
}

export interface RosterPick {
  round: number;
  golferName: string;
  ownershipPct: number;
  golferId: string;
}

export interface RosterTeam {
  teamId: string;
  teamNumber: number | null;
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

// Mirrors backend com.cwfgw.tournaments.FinalizedTournament. importWarnings holds one entry per tournament the
// finalize imported into, a clean one included, so [] means no ESPN event was imported.
export interface FinalizedTournament {
  tournament: Tournament;
  importWarnings: EspnImportWarnings[];
}

// Mirrors backend com.cwfgw.espn.EspnImportWarnings: a created golfer is listed under its most severe kind only.
export interface EspnImportWarnings {
  tournamentId: string;
  tournamentName: string;
  rosteredSurnameTwins: RosteredSurnameTwin[];
  createdGolfers: Golfer[];
  unmatchedCompetitors: UnmatchedCompetitor[];
  contestedCompetitors: ContestedCompetitor[];
}

export interface RosteredSurnameTwin {
  created: Golfer;
  rostered: Golfer[];
}

export interface UnmatchedCompetitor {
  espnCompetitorId: string;
  name: string;
}

export interface ContestedCompetitor {
  espnCompetitorId: string;
  name: string;
  candidates: Golfer[];
}

// Mirrors the backend's sealed CurrentTournament (com.cwfgw.tournaments), discriminated by `type`.
export type CurrentTournament =
  | { type: 'unfinalized'; tournamentId: string }
  | { type: 'season_finalized'; tournamentId: string }
  | { type: 'no_tournaments' };

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

// A candidate tournament from GET-ing ESPN's calendar for a date range,
// before anything is written to the DB. The operator drops rows that
// aren't part of the league's season (e.g. the Presidents Cup) before
// confirming — see UploadScheduleSection.
export interface PreviewedTournament {
  espnEventId: string;
  name: string;
  startDate: string;
  endDate: string;
  week: string;
}

export interface SeasonSchedulePreviewResult {
  entries: PreviewedTournament[];
  skipped: SkippedSeasonImportEntry[];
}

export interface ConfirmedTournamentEntry {
  espnEventId: string;
  name: string;
  startDate: string;
  endDate: string;
}

// Mirrors the backend's sealed GolferCandidate (com.cwfgw.admin.GolferCandidate).
// An `espn` candidate is not yet a golfer row; confirming it as an `espn` assignment inserts one.
export type GolferCandidate =
  | { type: 'existing'; golferId: string; name: string }
  | { type: 'espn'; espnId: string; name: string };

// Mirrors the backend's sealed PickMatch hierarchy
// (com.cwfgw.admin.PickMatch). kotlinx.serialization emits a "type"
// discriminator with @SerialName values matched/matched_espn/matched_backfill/ambiguous/no_match.
// A `matched_backfill` pick is an existing golfer without an ESPN id; confirming it as an `espn` assignment adds the id.
export type PickMatch =
  | { type: 'matched'; golferId: string; golferName: string }
  | { type: 'matched_espn'; espnId: string; golferName: string }
  | { type: 'matched_backfill'; espnId: string; golferName: string }
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
// (com.cwfgw.admin.GolferAssignment). new and espn carry a whole name,
// which the server splits; the UI never does.
export type GolferAssignment =
  | { type: 'existing'; golferId: string }
  | { type: 'new'; name: string }
  | { type: 'espn'; espnId: string; name: string };

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
  tournamentsReset: number;
}

// Mirrors backend com.cwfgw.golfers.Golfer. Returned by GET /api/v1/golfers.
export interface Golfer {
  id: string;
  pgaPlayerId: string | null;
  firstName: string;
  lastName: string;
  country: string | null;
  worldRanking: number | null;
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

// Mirrors backend com.cwfgw.tournamentLinks.CreateLinkedGolferRequest. The server splits `name`, as an import would.
export interface CreateLinkedGolferRequest {
  espnCompetitorId: string;
  name: string;
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
  totalEarnings: number;
  topTens: number;
  results: GolferHistoryEntry[];
}
