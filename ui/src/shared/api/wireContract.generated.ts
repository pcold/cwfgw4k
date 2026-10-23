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

import type { ExactFields, ExactRequestFields, ExactValues } from './exactFields';
import type {
  CleanSeasonResult,
  ConfirmRosterRequest,
  ConfirmSeasonScheduleRequest,
  ConfirmedTournamentEntry,
  ContestedCompetitor,
  CreateLeagueRequest,
  CreateLinkedGolferRequest,
  CreateSeasonRequest,
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
  HoleScore,
  LeaderboardPlayer,
  League,
  LiveLeaderboardEntry,
  LoginRequest,
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
  RoundScorecard,
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
  TournamentStatus,
  UndraftedGolfer,
  UnmatchedCompetitor,
  UpdateTournamentRequest,
  UploadSeasonRequest,
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
  'com.cwfgw.admin.ConfirmRosterRequest': ExactRequestFields<
    ConfirmRosterRequest,
    | 'seasonId'
    | 'teams',
    never
  >;
  'com.cwfgw.admin.ConfirmSeasonScheduleRequest': ExactRequestFields<
    ConfirmSeasonScheduleRequest,
    | 'entries',
    never
  >;
  'com.cwfgw.admin.ConfirmedPick': ExactRequestFields<
    RosterConfirmPick,
    | 'round'
    | 'ownershipPct'
    | 'assignment',
    never
  >;
  'com.cwfgw.admin.ConfirmedTeam': ExactRequestFields<
    RosterConfirmTeamInput,
    | 'teamNumber'
    | 'teamName'
    | 'picks',
    never
  >;
  'com.cwfgw.admin.ConfirmedTournamentEntry': ExactRequestFields<
    ConfirmedTournamentEntry,
    | 'espnEventId'
    | 'name'
    | 'startDate'
    | 'endDate',
    never
  >;
  'com.cwfgw.admin.GolferAssignment': ExactValues<
    GolferAssignment['type'],
    | 'espn'
    | 'existing'
    | 'new'
  >;
  'com.cwfgw.admin.GolferAssignment#espn': ExactRequestFields<
    Extract<GolferAssignment, { type: 'espn' }>,
    | 'type'
    | 'espnId'
    | 'name',
    never
  >;
  'com.cwfgw.admin.GolferAssignment#existing': ExactRequestFields<
    Extract<GolferAssignment, { type: 'existing' }>,
    | 'type'
    | 'golferId',
    never
  >;
  'com.cwfgw.admin.GolferAssignment#new': ExactRequestFields<
    Extract<GolferAssignment, { type: 'new' }>,
    | 'type'
    | 'name',
    never
  >;
  'com.cwfgw.admin.GolferCandidate': ExactValues<
    GolferCandidate['type'],
    | 'espn'
    | 'existing'
  >;
  'com.cwfgw.admin.GolferCandidate#espn': ExactFields<
    Extract<GolferCandidate, { type: 'espn' }>,
    | 'type'
    | 'espnId'
    | 'name'
  >;
  'com.cwfgw.admin.GolferCandidate#existing': ExactFields<
    Extract<GolferCandidate, { type: 'existing' }>,
    | 'type'
    | 'golferId'
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
    | 'golferId'
    | 'golferName'
  >;
  'com.cwfgw.admin.PickMatch#matched_backfill': ExactFields<
    Extract<PickMatch, { type: 'matched_backfill' }>,
    | 'type'
    | 'espnId'
    | 'golferName'
  >;
  'com.cwfgw.admin.PickMatch#matched_espn': ExactFields<
    Extract<PickMatch, { type: 'matched_espn' }>,
    | 'type'
    | 'espnId'
    | 'golferName'
  >;
  'com.cwfgw.admin.PickMatch#no_match': ExactFields<
    Extract<PickMatch, { type: 'no_match' }>,
    | 'type'
  >;
  'com.cwfgw.admin.PreviewPick': ExactFields<
    RosterPreviewPick,
    | 'round'
    | 'playerName'
    | 'ownershipPct'
    | 'match'
  >;
  'com.cwfgw.admin.PreviewTeam': ExactFields<
    RosterPreviewTeam,
    | 'teamNumber'
    | 'teamName'
    | 'picks'
  >;
  'com.cwfgw.admin.PreviewedTournament': ExactFields<
    PreviewedTournament,
    | 'espnEventId'
    | 'name'
    | 'startDate'
    | 'endDate'
    | 'week'
  >;
  'com.cwfgw.admin.RosterPreviewResult': ExactFields<
    RosterPreview,
    | 'teams'
    | 'totalPicks'
    | 'matchedCount'
    | 'ambiguousCount'
    | 'unmatchedCount'
  >;
  'com.cwfgw.admin.RosterUploadResult': ExactFields<
    RosterConfirmResult,
    | 'teamsCreated'
    | 'golfersCreated'
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
    | 'espnEventId'
    | 'espnEventName'
    | 'reason'
  >;
  'com.cwfgw.admin.UploadSeasonRequest': ExactRequestFields<
    UploadSeasonRequest,
    | 'startDate'
    | 'endDate',
    never
  >;
  'com.cwfgw.datahealth.DataHealthCheck': ExactFields<
    DataHealthCheck<unknown>,
    | 'rows'
    | 'count'
  >;
  'com.cwfgw.datahealth.DataHealthGolfer': ExactFields<
    DataHealthGolfer,
    | 'id'
    | 'firstName'
    | 'lastName'
    | 'pgaPlayerId'
  >;
  'com.cwfgw.datahealth.DataHealthReport': ExactFields<
    DataHealthReport,
    | 'rosteredTwins'
    | 'rosteredWithoutEspnId'
    | 'blankFirstNames'
  >;
  'com.cwfgw.datahealth.RosteredTwinGroup': ExactFields<
    RosteredTwinGroup,
    | 'normalizedName'
    | 'rosteredWithoutEspnId'
    | 'holdingEspnId'
  >;
  'com.cwfgw.espn.ContestedCompetitor': ExactFields<
    ContestedCompetitor,
    | 'espnCompetitorId'
    | 'name'
    | 'candidates'
  >;
  'com.cwfgw.espn.EspnImportWarnings': ExactFields<
    EspnImportWarnings,
    | 'tournamentId'
    | 'tournamentName'
    | 'rosteredSurnameTwins'
    | 'createdGolfers'
    | 'unmatchedCompetitors'
    | 'contestedCompetitors'
  >;
  'com.cwfgw.espn.RosteredSurnameTwin': ExactFields<
    RosteredSurnameTwin,
    | 'created'
    | 'rostered'
  >;
  'com.cwfgw.espn.UnmatchedCompetitor': ExactFields<
    UnmatchedCompetitor,
    | 'espnCompetitorId'
    | 'name'
  >;
  'com.cwfgw.golfers.Golfer': ExactFields<
    Golfer,
    | 'id'
    | 'pgaPlayerId'
    | 'firstName'
    | 'lastName'
    | 'country'
    | 'worldRanking'
    | 'updatedAt'
  >;
  'com.cwfgw.leagues.CreateLeagueRequest': ExactRequestFields<
    CreateLeagueRequest,
    | 'name',
    never
  >;
  'com.cwfgw.leagues.League': ExactFields<
    League,
    | 'id'
    | 'name'
    | 'createdAt'
  >;
  'com.cwfgw.reports.GolferHistory': ExactFields<
    GolferHistory,
    | 'golferName'
    | 'totalEarnings'
    | 'topTens'
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
    | 'teamNames'
  >;
  'com.cwfgw.reports.LiveLeaderboardEntry': ExactFields<
    LiveLeaderboardEntry,
    | 'position'
    | 'scoreToPar'
    | 'players'
    | 'roundScores'
    | 'totalStrokes'
    | 'scorecards'
    | 'rostered'
  >;
  'com.cwfgw.reports.PlayerRankings': ExactFields<
    PlayerRankings,
    | 'players'
    | 'live'
    | 'liveEligible'
  >;
  'com.cwfgw.reports.PlayerRankingsRow': ExactFields<
    PlayerRankingsRow,
    | 'key'
    | 'golferId'
    | 'name'
    | 'topTens'
    | 'totalEarnings'
    | 'teamName'
    | 'draftRound'
  >;
  'com.cwfgw.reports.Rankings': ExactFields<
    Rankings,
    | 'teams'
    | 'weeks'
    | 'tournamentNames'
    | 'live'
    | 'liveEligible'
    | 'cashTotals'
    | 'standings'
  >;
  'com.cwfgw.reports.ReportCell': ExactFields<
    ReportCell,
    | 'round'
    | 'golferName'
    | 'golferId'
    | 'positionStr'
    | 'finish'
    | 'earnings'
    | 'ownershipPct'
    | 'seasonEarnings'
    | 'seasonTopTens'
    | 'inField'
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
    | 'amountPerTeam'
    | 'rounds'
  >;
  'com.cwfgw.reports.ReportSideBetTeamEntry': ExactFields<
    ReportSideBetTeamEntry,
    | 'teamId'
    | 'teamName'
    | 'golferName'
    | 'cumulativeEarnings'
    | 'payout'
    | 'outcome'
  >;
  'com.cwfgw.reports.ReportTeamColumn': ExactFields<
    ReportTeamColumn,
    | 'teamId'
    | 'teamName'
    | 'cells'
    | 'topTenEarnings'
    | 'weeklyTotal'
    | 'previous'
    | 'subtotal'
    | 'topTenCount'
    | 'topTenMoney'
    | 'sideBets'
    | 'totalCash'
  >;
  'com.cwfgw.reports.ReportTournamentInfo': ExactFields<
    ReportTournamentInfo,
    | 'name'
    | 'startDate'
    | 'endDate'
    | 'status'
    | 'payoutMultiplier'
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
    | 'teamName'
  >;
  'com.cwfgw.reports.TeamRanking': ExactFields<
    TeamRanking,
    | 'teamId'
    | 'teamName'
    | 'subtotal'
    | 'sideBets'
    | 'totalCash'
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
    | 'teamId'
    | 'tied'
    | 'position'
    | 'behind'
    | 'behindLeader'
  >;
  'com.cwfgw.reports.TeamStanding#leader': ExactFields<
    Extract<TeamStanding, { type: 'leader' }>,
    | 'type'
    | 'teamId'
    | 'tied'
    | 'aheadOf'
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
    | 'undraftedTopTens'
    | 'sideBetRules'
    | 'sideBetDetail'
    | 'standingsOrder'
    | 'live'
    | 'liveLeaderboard'
    | 'liveEligible'
    | 'awaitingTeeOff'
    | 'cashTotals'
    | 'sideBetTotals'
    | 'weeklyTotals'
    | 'weeklyStandings'
    | 'sideBetStandings'
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
    | 'scoresDeleted'
    | 'resultsDeleted'
    | 'tournamentsReset'
  >;
  'com.cwfgw.seasons.CreateSeasonRequest': ExactRequestFields<
    CreateSeasonRequest,
    | 'leagueId'
    | 'name'
    | 'seasonYear',
    | 'seasonNumber'
    | 'maxTeams'
    | 'rules'
  >;
  'com.cwfgw.seasons.Season': ExactFields<
    Season,
    | 'id'
    | 'leagueId'
    | 'name'
    | 'seasonYear'
    | 'seasonNumber'
    | 'tieFloor'
    | 'sideBetAmount'
    | 'maxTeams'
    | 'createdAt'
    | 'updatedAt'
  >;
  'com.cwfgw.seasons.SeasonRules': ExactFields<
    SeasonRules,
    | 'payouts'
    | 'tieFloor'
    | 'sideBetRounds'
    | 'sideBetAmount'
  >;
  'com.cwfgw.teams.RosterViewPick': ExactFields<
    RosterPick,
    | 'round'
    | 'golferName'
    | 'ownershipPct'
    | 'golferId'
  >;
  'com.cwfgw.teams.RosterViewTeam': ExactFields<
    RosterTeam,
    | 'teamId'
    | 'teamNumber'
    | 'teamName'
    | 'picks'
  >;
  'com.cwfgw.teams.Team': ExactFields<
    Team,
    | 'id'
    | 'seasonId'
    | 'ownerName'
    | 'teamName'
    | 'teamNumber'
    | 'createdAt'
    | 'updatedAt'
  >;
  'com.cwfgw.tournamentLinks.CreateLinkedGolferRequest': ExactRequestFields<
    CreateLinkedGolferRequest,
    | 'espnCompetitorId'
    | 'name',
    never
  >;
  'com.cwfgw.tournamentLinks.TournamentCompetitorListing': ExactFields<
    TournamentCompetitorListing,
    | 'tournamentId'
    | 'isFinalized'
    | 'competitors'
  >;
  'com.cwfgw.tournamentLinks.TournamentCompetitorView': ExactFields<
    TournamentCompetitorView,
    | 'espnCompetitorId'
    | 'name'
    | 'position'
    | 'isTeamPartner'
    | 'linkedGolfer'
    | 'hasOverride'
  >;
  'com.cwfgw.tournamentLinks.TournamentPlayerOverride': ExactFields<
    TournamentPlayerOverride,
    | 'tournamentId'
    | 'espnCompetitorId'
    | 'golferId'
  >;
  'com.cwfgw.tournamentLinks.UpsertTournamentPlayerOverrideRequest': ExactRequestFields<
    UpsertTournamentPlayerOverrideRequest,
    | 'espnCompetitorId'
    | 'golferId',
    never
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
    | 'tournamentId'
  >;
  'com.cwfgw.tournaments.CurrentTournament#unfinalized': ExactFields<
    Extract<CurrentTournament, { type: 'unfinalized' }>,
    | 'type'
    | 'tournamentId'
  >;
  'com.cwfgw.tournaments.FinalizedTournament': ExactFields<
    FinalizedTournament,
    | 'tournament'
    | 'importWarnings'
  >;
  'com.cwfgw.tournaments.HoleScore': ExactFields<
    HoleScore,
    | 'hole'
    | 'par'
    | 'strokes'
    | 'toPar'
  >;
  'com.cwfgw.tournaments.RoundScorecard': ExactFields<
    RoundScorecard,
    | 'round'
    | 'holes'
    | 'frontNine'
    | 'backNine'
    | 'total'
    | 'frontPar'
    | 'backPar'
    | 'totalPar'
  >;
  'com.cwfgw.tournaments.Tournament': ExactFields<
    Tournament,
    | 'id'
    | 'pgaTournamentId'
    | 'name'
    | 'seasonId'
    | 'startDate'
    | 'endDate'
    | 'courseName'
    | 'status'
    | 'purseAmount'
    | 'payoutMultiplier'
    | 'week'
    | 'isTeamEvent'
    | 'createdAt'
  >;
  'com.cwfgw.tournaments.TournamentStatus': ExactValues<
    TournamentStatus,
    | 'upcoming'
    | 'in_progress'
    | 'completed'
  >;
  'com.cwfgw.tournaments.UpdateTournamentRequest': ExactRequestFields<
    UpdateTournamentRequest,
    never,
    | 'name'
    | 'startDate'
    | 'endDate'
    | 'courseName'
    | 'status'
    | 'purseAmount'
    | 'payoutMultiplier'
    | 'isTeamEvent'
  >;
  'com.cwfgw.users.LoginRequest': ExactRequestFields<
    LoginRequest,
    | 'username'
    | 'password',
    never
  >;
  'com.cwfgw.users.User': ExactFields<
    User,
    | 'id'
    | 'username'
    | 'role'
    | 'createdAt'
  >;
  'com.cwfgw.users.UserRole': ExactValues<
    UserRole,
    | 'admin'
    | 'user'
  >;
}
