package com.cwfgw.http

import com.cwfgw.admin.ConfirmRosterRequest
import com.cwfgw.admin.ConfirmSeasonScheduleRequest
import com.cwfgw.admin.RosterPreviewResult
import com.cwfgw.admin.RosterUploadResult
import com.cwfgw.admin.SeasonImportResult
import com.cwfgw.admin.SeasonSchedulePreviewResult
import com.cwfgw.admin.UploadSeasonRequest
import com.cwfgw.datahealth.DataHealthReport
import com.cwfgw.golfers.Golfer
import com.cwfgw.health.HealthResponse
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.League
import com.cwfgw.reports.GolferHistory
import com.cwfgw.reports.PlayerRankings
import com.cwfgw.reports.Rankings
import com.cwfgw.reports.WeeklyReport
import com.cwfgw.seasons.CleanSeasonResult
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.Season
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.RosterViewTeam
import com.cwfgw.tournamentLinks.CreateLinkedGolferRequest
import com.cwfgw.tournamentLinks.TournamentCompetitorListing
import com.cwfgw.tournamentLinks.TournamentPlayerOverride
import com.cwfgw.tournamentLinks.UpsertTournamentPlayerOverrideRequest
import com.cwfgw.tournaments.CurrentTournament
import com.cwfgw.tournaments.FinalizedTournament
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.UpdateTournamentRequest
import com.cwfgw.users.LoginRequest
import com.cwfgw.users.User
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import java.io.File

internal val MAIN_SOURCE_ROOTS = listOf(File("src/main/kotlin"))

/**
 * Every type a route receives or responds with, as a list's element where it responds with a list: the
 * roots of the wire-contract snapshot. Listed, not scanned: a handler responds with a service result, so
 * no route file names the type. A type left off fails the accounting test, being `@Serializable` and unreached.
 */
internal val WIRE_TYPES: List<SerialDescriptor> =
    listOf(
        serializer<CleanSeasonResult>(),
        serializer<ConfirmRosterRequest>(),
        serializer<ConfirmSeasonScheduleRequest>(),
        serializer<CreateLeagueRequest>(),
        serializer<CreateLinkedGolferRequest>(),
        serializer<CreateSeasonRequest>(),
        serializer<CurrentTournament>(),
        serializer<DataHealthReport>(),
        serializer<ErrorBody>(),
        serializer<FinalizedTournament>(),
        serializer<Golfer>(),
        serializer<GolferHistory>(),
        serializer<HealthResponse>(),
        serializer<League>(),
        serializer<LoginRequest>(),
        serializer<PlayerRankings>(),
        serializer<Rankings>(),
        serializer<RefusalBody>(),
        serializer<RosterPreviewResult>(),
        serializer<RosterUploadResult>(),
        serializer<RosterViewTeam>(),
        serializer<Season>(),
        serializer<SeasonImportResult>(),
        serializer<SeasonRules>(),
        serializer<SeasonSchedulePreviewResult>(),
        serializer<Tournament>(),
        serializer<TournamentCompetitorListing>(),
        serializer<TournamentPlayerOverride>(),
        serializer<UpdateTournamentRequest>(),
        serializer<UploadSeasonRequest>(),
        serializer<UpsertTournamentPlayerOverrideRequest>(),
        serializer<User>(),
        serializer<WeeklyReport>(),
    ).map { it.descriptor }
