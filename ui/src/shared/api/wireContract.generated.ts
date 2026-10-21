/*
 * Generated from the Kotlin serializer descriptors by WireContractSpec. Do not hand-edit: a key that
 * belongs here is a property on a type under src/main/kotlin. A conflict is regenerated, never merged.
 *
 *     ./gradlew test -x uiBuild -Dkotest.filter.specs='*WireContractSpec'
 *     cp build/wireContract.generated.ts ui/src/shared/api/wireContract.generated.ts
 *
 * `-x uiBuild` because :test compiles this file before running, and a stale one is what fails that
 * compile; without it the command that regenerates the file cannot reach the code that writes it.
 */

import type { ExactFields, ExactValues } from './exactFields';
import type {
  CleanSeasonResult,
  ConfirmedTournamentEntry,
  ContestedCompetitor,
  CreateLinkedGolferRequest,
  CurrentTournament,
  DataHealthCheck,
  DataHealthGolfer,
  DataHealthReport,
  EspnImportWarnings,
  FinalizedTournament,
  Golfer,
  GolferAssignment,
  GolferCandidate,
  GolferHistory,
  GolferHistoryEntry,
  LeaderboardPlayer,
  League,
  LiveLeaderboardEntry,
  PickMatch,
  PlayerRankings,
  PlayerRankingsRow,
  PreviewedTournament,
  Rankings,
  ReportCell,
  ReportCellFinish,
  ReportScope,
  ReportSideBetRound,
  ReportSideBetRules,
  ReportSideBetTeamEntry,
  ReportTeamColumn,
  ReportTournamentInfo,
  RosterConfirmPick,
  RosterConfirmResult,
  RosterConfirmTeamInput,
  RosterPick,
  RosterPreview,
  RosterPreviewPick,
  RosterPreviewTeam,
  RosterTeam,
  RosteredSurnameTwin,
  RosteredTwinGroup,
  Season,
  SeasonImportResult,
  SeasonRules,
  SeasonSchedulePreviewResult,
  SideBetOutcome,
  SkippedSeasonImportEntry,
  StandingGap,
  StandingsEntry,
  Team,
  TeamRanking,
  TeamStanding,
  Tournament,
  TournamentCompetitorListing,
  TournamentCompetitorView,
  TournamentPlayerOverride,
  UndraftedGolfer,
  UnmatchedCompetitor,
  UpsertTournamentPlayerOverrideRequest,
  User,
  UserRole,
  WeeklyReport,
  WonLostTotals,
} from './types';

/**
 * Nothing imports this, and it still fails the build: `tsconfig.app.json` includes every file under
 * `src`, and each member is a verdict standing under an index signature of `true`.
 */
export interface WireContract extends Record<string, true> {
  'com.cwfgw.admin.ConfirmedPick': ExactFields<
    RosterConfirmPick,
    | 'round'
    | 'ownership_pct'
    | 'assignment'
  >;
  'com.cwfgw.admin.ConfirmedTeam': ExactFields<
    RosterConfirmTeamInput,
    | 'team_number'
    | 'team_name'
    | 'picks'
  >;
  'com.cwfgw.admin.ConfirmedTournamentEntry': ExactFields<
    ConfirmedTournamentEntry,
    | 'espn_event_id'
    | 'name'
    | 'start_date'
    | 'end_date'
  >;
  'com.cwfgw.admin.GolferAssignment': ExactValues<
    GolferAssignment['type'],
    | 'espn'
    | 'existing'
    | 'new'
  >;
  'com.cwfgw.admin.GolferAssignment#espn': ExactFields<
    Extract<GolferAssignment, { type: 'espn' }>,
    | 'type'
    | 'espn_id'
    | 'name'
  >;
  'com.cwfgw.admin.GolferAssignment#existing': ExactFields<
    Extract<GolferAssignment, { type: 'existing' }>,
    | 'type'
    | 'golfer_id'
  >;
  'com.cwfgw.admin.GolferAssignment#new': ExactFields<
    Extract<GolferAssignment, { type: 'new' }>,
    | 'type'
    | 'name'
  >;
  'com.cwfgw.admin.GolferCandidate': ExactValues<
    GolferCandidate['type'],
    | 'espn'
    | 'existing'
  >;
  'com.cwfgw.admin.GolferCandidate#espn': ExactFields<
    Extract<GolferCandidate, { type: 'espn' }>,
    | 'type'
    | 'espn_id'
    | 'name'
  >;
  'com.cwfgw.admin.GolferCandidate#existing': ExactFields<
    Extract<GolferCandidate, { type: 'existing' }>,
    | 'type'
    | 'golfer_id'
    | 'name'
  >;
  'com.cwfgw.admin.PickMatch': ExactValues<
    PickMatch['type'],
    | 'ambiguous'
    | 'matched'
    | 'matched_backfill'
    | 'matched_espn'
    | 'no_match'
  >;
  'com.cwfgw.admin.PickMatch#ambiguous': ExactFields<
    Extract<PickMatch, { type: 'ambiguous' }>,
    | 'type'
    | 'candidates'
  >;
  'com.cwfgw.admin.PickMatch#matched': ExactFields<
    Extract<PickMatch, { type: 'matched' }>,
    | 'type'
    | 'golfer_id'
    | 'golfer_name'
  >;
  'com.cwfgw.admin.PickMatch#matched_backfill': ExactFields<
    Extract<PickMatch, { type: 'matched_backfill' }>,
    | 'type'
    | 'espn_id'
    | 'golfer_name'
  >;
  'com.cwfgw.admin.PickMatch#matched_espn': ExactFields<
    Extract<PickMatch, { type: 'matched_espn' }>,
    | 'type'
    | 'espn_id'
    | 'golfer_name'
  >;
  'com.cwfgw.admin.PickMatch#no_match': ExactFields<
    Extract<PickMatch, { type: 'no_match' }>,
    | 'type'
  >;
  'com.cwfgw.admin.PreviewPick': ExactFields<
    RosterPreviewPick,
    | 'round'
    | 'player_name'
    | 'ownership_pct'
    | 'match'
  >;
  'com.cwfgw.admin.PreviewTeam': ExactFields<
    RosterPreviewTeam,
    | 'team_number'
    | 'team_name'
    | 'picks'
  >;
  'com.cwfgw.admin.PreviewedTournament': ExactFields<
    PreviewedTournament,
    | 'espn_event_id'
    | 'name'
    | 'start_date'
    | 'end_date'
    | 'week'
  >;
  'com.cwfgw.admin.RosterPreviewResult': ExactFields<
    RosterPreview,
    | 'teams'
    | 'total_picks'
    | 'matched_count'
    | 'ambiguous_count'
    | 'unmatched_count'
  >;
  'com.cwfgw.admin.RosterUploadResult': ExactFields<
    RosterConfirmResult,
    | 'teams_created'
    | 'golfers_created'
    | 'teams'
  >;
  'com.cwfgw.admin.SeasonImportResult': ExactFields<
    SeasonImportResult,
    | 'created'
    | 'skipped'
  >;
  'com.cwfgw.admin.SeasonSchedulePreviewResult': ExactFields<
    SeasonSchedulePreviewResult,
    | 'entries'
    | 'skipped'
  >;
  'com.cwfgw.admin.SkippedEntry': ExactFields<
    SkippedSeasonImportEntry,
    | 'espn_event_id'
    | 'espn_event_name'
    | 'reason'
  >;
  'com.cwfgw.datahealth.DataHealthCheck': ExactFields<
    DataHealthCheck<unknown>,
    | 'rows'
    | 'count'
  >;
  'com.cwfgw.datahealth.DataHealthGolfer': ExactFields<
    DataHealthGolfer,
    | 'id'
    | 'first_name'
    | 'last_name'
    | 'pga_player_id'
  >;
  'com.cwfgw.datahealth.DataHealthReport': ExactFields<
    DataHealthReport,
    | 'rostered_twins'
    | 'rostered_without_espn_id'
    | 'blank_first_names'
  >;
  'com.cwfgw.datahealth.RosteredTwinGroup': ExactFields<
    RosteredTwinGroup,
    | 'normalized_name'
    | 'rostered_without_espn_id'
    | 'holding_espn_id'
  >;
  'com.cwfgw.espn.ContestedCompetitor': ExactFields<
    ContestedCompetitor,
    | 'espn_competitor_id'
    | 'name'
    | 'candidates'
  >;
  'com.cwfgw.espn.EspnImportWarnings': ExactFields<
    EspnImportWarnings,
    | 'tournament_id'
    | 'tournament_name'
    | 'rostered_surname_twins'
    | 'created_golfers'
    | 'unmatched_competitors'
    | 'contested_competitors'
  >;
  'com.cwfgw.espn.RosteredSurnameTwin': ExactFields<
    RosteredSurnameTwin,
    | 'created'
    | 'rostered'
  >;
  'com.cwfgw.espn.UnmatchedCompetitor': ExactFields<
    UnmatchedCompetitor,
    | 'espn_competitor_id'
    | 'name'
  >;
  'com.cwfgw.golfers.Golfer': ExactFields<
    Golfer,
    | 'id'
    | 'pga_player_id'
    | 'first_name'
    | 'last_name'
    | 'country'
    | 'world_ranking'
    | 'updated_at'
  >;
  'com.cwfgw.leagues.League': ExactFields<
    League,
    | 'id'
    | 'name'
    | 'created_at'
  >;
  'com.cwfgw.reports.GolferHistory': ExactFields<
    GolferHistory,
    | 'golfer_name'
    | 'total_earnings'
    | 'top_tens'
    | 'results'
  >;
  'com.cwfgw.reports.GolferHistoryEntry': ExactFields<
    GolferHistoryEntry,
    | 'tournament'
    | 'position'
    | 'earnings'
  >;
  'com.cwfgw.reports.LeaderboardPlayer': ExactFields<
    LeaderboardPlayer,
    | 'name'
    | 'team_names'
  >;
  'com.cwfgw.reports.LiveLeaderboardEntry': ExactFields<
    LiveLeaderboardEntry,
    | 'position'
    | 'score_to_par'
    | 'players'
    | 'round_scores'
    | 'total_strokes'
    | 'rostered'
  >;
  'com.cwfgw.reports.PlayerRankings': ExactFields<
    PlayerRankings,
    | 'players'
    | 'live'
    | 'live_eligible'
  >;
  'com.cwfgw.reports.PlayerRankingsRow': ExactFields<
    PlayerRankingsRow,
    | 'key'
    | 'golfer_id'
    | 'name'
    | 'top_tens'
    | 'total_earnings'
    | 'team_name'
    | 'draft_round'
  >;
  'com.cwfgw.reports.Rankings': ExactFields<
    Rankings,
    | 'teams'
    | 'weeks'
    | 'tournament_names'
    | 'live'
    | 'live_eligible'
    | 'cash_totals'
    | 'standings'
  >;
  'com.cwfgw.reports.ReportCell': ExactFields<
    ReportCell,
    | 'round'
    | 'golfer_name'
    | 'golfer_id'
    | 'position_str'
    | 'finish'
    | 'earnings'
    | 'ownership_pct'
    | 'season_earnings'
    | 'season_top_tens'
    | 'in_field'
  >;
  'com.cwfgw.reports.ReportCellFinish': ExactFields<
    ReportCellFinish,
    | 'position'
    | 'tied'
  >;
  'com.cwfgw.reports.ReportScope': ExactValues<
    ReportScope,
    | 'season'
    | 'tournament'
  >;
  'com.cwfgw.reports.ReportSideBetRound': ExactFields<
    ReportSideBetRound,
    | 'round'
    | 'teams'
  >;
  'com.cwfgw.reports.ReportSideBetRules': ExactFields<
    ReportSideBetRules,
    | 'amount_per_team'
    | 'rounds'
  >;
  'com.cwfgw.reports.ReportSideBetTeamEntry': ExactFields<
    ReportSideBetTeamEntry,
    | 'team_id'
    | 'team_name'
    | 'golfer_name'
    | 'cumulative_earnings'
    | 'payout'
    | 'outcome'
  >;
  'com.cwfgw.reports.ReportTeamColumn': ExactFields<
    ReportTeamColumn,
    | 'team_id'
    | 'team_name'
    | 'cells'
    | 'top_ten_earnings'
    | 'weekly_total'
    | 'previous'
    | 'subtotal'
    | 'top_ten_count'
    | 'top_ten_money'
    | 'side_bets'
    | 'total_cash'
  >;
  'com.cwfgw.reports.ReportTournamentInfo': ExactFields<
    ReportTournamentInfo,
    | 'name'
    | 'start_date'
    | 'end_date'
    | 'status'
    | 'payout_multiplier'
    | 'week'
  >;
  'com.cwfgw.reports.SideBetOutcome': ExactValues<
    SideBetOutcome,
    | 'won'
    | 'lost'
  >;
  'com.cwfgw.reports.StandingGap': ExactFields<
    StandingGap,
    | 'position'
    | 'tied'
    | 'amount'
  >;
  'com.cwfgw.reports.StandingsEntry': ExactFields<
    StandingsEntry,
    | 'rank'
    | 'team_name'
  >;
  'com.cwfgw.reports.TeamRanking': ExactFields<
    TeamRanking,
    | 'team_id'
    | 'team_name'
    | 'subtotal'
    | 'side_bets'
    | 'total_cash'
    | 'series'
  >;
  'com.cwfgw.reports.TeamStanding': ExactValues<
    TeamStanding['type'],
    | 'chaser'
    | 'leader'
  >;
  'com.cwfgw.reports.TeamStanding#chaser': ExactFields<
    Extract<TeamStanding, { type: 'chaser' }>,
    | 'type'
    | 'team_id'
    | 'tied'
    | 'position'
    | 'behind'
    | 'behind_leader'
  >;
  'com.cwfgw.reports.TeamStanding#leader': ExactFields<
    Extract<TeamStanding, { type: 'leader' }>,
    | 'type'
    | 'team_id'
    | 'tied'
    | 'ahead_of'
  >;
  'com.cwfgw.reports.UndraftedGolfer': ExactFields<
    UndraftedGolfer,
    | 'name'
    | 'payout'
  >;
  'com.cwfgw.reports.WeeklyReport': ExactFields<
    WeeklyReport,
    | 'scope'
    | 'tournament'
    | 'teams'
    | 'undrafted_top_tens'
    | 'side_bet_rules'
    | 'side_bet_detail'
    | 'standings_order'
    | 'live'
    | 'live_leaderboard'
    | 'live_eligible'
    | 'awaiting_tee_off'
    | 'cash_totals'
    | 'side_bet_totals'
    | 'weekly_totals'
    | 'weekly_standings'
    | 'side_bet_standings'
    | 'finalizable'
  >;
  'com.cwfgw.reports.WonLostTotals': ExactFields<
    WonLostTotals,
    | 'won'
    | 'lost'
    | 'net'
  >;
  'com.cwfgw.seasons.CleanSeasonResult': ExactFields<
    CleanSeasonResult,
    | 'scores_deleted'
    | 'results_deleted'
    | 'tournaments_reset'
  >;
  'com.cwfgw.seasons.Season': ExactFields<
    Season,
    | 'id'
    | 'league_id'
    | 'name'
    | 'season_year'
    | 'season_number'
    | 'tie_floor'
    | 'side_bet_amount'
    | 'max_teams'
    | 'created_at'
    | 'updated_at'
  >;
  'com.cwfgw.seasons.SeasonRules': ExactFields<
    SeasonRules,
    | 'payouts'
    | 'tie_floor'
    | 'side_bet_rounds'
    | 'side_bet_amount'
  >;
  'com.cwfgw.teams.RosterViewPick': ExactFields<
    RosterPick,
    | 'round'
    | 'golfer_name'
    | 'ownership_pct'
    | 'golfer_id'
  >;
  'com.cwfgw.teams.RosterViewTeam': ExactFields<
    RosterTeam,
    | 'team_id'
    | 'team_number'
    | 'team_name'
    | 'picks'
  >;
  'com.cwfgw.teams.Team': ExactFields<
    Team,
    | 'id'
    | 'season_id'
    | 'owner_name'
    | 'team_name'
    | 'team_number'
    | 'created_at'
    | 'updated_at'
  >;
  'com.cwfgw.tournamentLinks.CreateLinkedGolferRequest': ExactFields<
    CreateLinkedGolferRequest,
    | 'espn_competitor_id'
    | 'name'
  >;
  'com.cwfgw.tournamentLinks.TournamentCompetitorListing': ExactFields<
    TournamentCompetitorListing,
    | 'tournament_id'
    | 'is_finalized'
    | 'competitors'
  >;
  'com.cwfgw.tournamentLinks.TournamentCompetitorView': ExactFields<
    TournamentCompetitorView,
    | 'espn_competitor_id'
    | 'name'
    | 'position'
    | 'is_team_partner'
    | 'linked_golfer'
    | 'has_override'
  >;
  'com.cwfgw.tournamentLinks.TournamentPlayerOverride': ExactFields<
    TournamentPlayerOverride,
    | 'tournament_id'
    | 'espn_competitor_id'
    | 'golfer_id'
  >;
  'com.cwfgw.tournamentLinks.UpsertTournamentPlayerOverrideRequest': ExactFields<
    UpsertTournamentPlayerOverrideRequest,
    | 'espn_competitor_id'
    | 'golfer_id'
  >;
  'com.cwfgw.tournaments.CurrentTournament': ExactValues<
    CurrentTournament['type'],
    | 'no_tournaments'
    | 'season_finalized'
    | 'unfinalized'
  >;
  'com.cwfgw.tournaments.CurrentTournament#no_tournaments': ExactFields<
    Extract<CurrentTournament, { type: 'no_tournaments' }>,
    | 'type'
  >;
  'com.cwfgw.tournaments.CurrentTournament#season_finalized': ExactFields<
    Extract<CurrentTournament, { type: 'season_finalized' }>,
    | 'type'
    | 'tournament_id'
  >;
  'com.cwfgw.tournaments.CurrentTournament#unfinalized': ExactFields<
    Extract<CurrentTournament, { type: 'unfinalized' }>,
    | 'type'
    | 'tournament_id'
  >;
  'com.cwfgw.tournaments.FinalizedTournament': ExactFields<
    FinalizedTournament,
    | 'tournament'
    | 'import_warnings'
  >;
  'com.cwfgw.tournaments.Tournament': ExactFields<
    Tournament,
    | 'id'
    | 'pga_tournament_id'
    | 'name'
    | 'season_id'
    | 'start_date'
    | 'end_date'
    | 'course_name'
    | 'status'
    | 'purse_amount'
    | 'payout_multiplier'
    | 'week'
    | 'is_team_event'
    | 'created_at'
  >;
  'com.cwfgw.users.User': ExactFields<
    User,
    | 'id'
    | 'username'
    | 'role'
    | 'created_at'
  >;
  'com.cwfgw.users.UserRole': ExactValues<
    UserRole,
    | 'admin'
    | 'user'
  >;
}
