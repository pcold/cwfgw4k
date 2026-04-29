package com.cwfgw.espn

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.toTournamentId
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger {}

fun Route.espnRoutes(service: EspnService) {
    route("/espn") {
        get("/calendar") { getCalendar(service) }
        get("/preview/{seasonId}") { previewByDate(service) }
        authenticate(SESSION_AUTH_NAME) {
            route("/import") {
                post { importByDate(service) }
                post("/tournament/{tournamentId}") { importForTournament(service) }
            }
        }
    }
}

private suspend fun RoutingContext.getCalendar(service: EspnService) {
    val entries =
        try {
            service.fetchCalendar()
        } catch (e: EspnUpstreamException) {
            log.warn(e) { "ESPN calendar fetch failed with status ${e.status}" }
            throw DomainError.BadGateway("ESPN returned ${e.status}", cause = e)
        }
    call.respond(entries)
}

private suspend fun RoutingContext.importByDate(service: EspnService) {
    requireAdmin()
    val date =
        optionalQueryParam("date", ::parseLocalDate)
            ?: throw DomainError.Validation("missing query parameter: date")
    call.respond(service.importByDate(date).orThrow())
}

private suspend fun RoutingContext.previewByDate(service: EspnService) {
    val seasonId = seasonId()
    val date =
        optionalQueryParam("date", ::parseLocalDate)
            ?: throw DomainError.Validation("missing query parameter: date")
    call.respond(service.previewByDate(seasonId, date).orThrow())
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["seasonId"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.importForTournament(service: EspnService) {
    requireAdmin()
    call.respond(service.importForTournament(tournamentId()).orThrow())
}

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["tournamentId"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private fun parseLocalDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        log.warn(e) { "Failed to parse date query parameter '$raw'" }
        null
    }

private fun EspnError.toDomainError(): DomainError =
    when (this) {
        is EspnError.UpstreamUnavailable -> DomainError.BadGateway("ESPN returned $status")
        is EspnError.TournamentNotFound -> DomainError.NotFound("tournament ${tournamentId.value} not found")
        is EspnError.TournamentNotLinked ->
            DomainError.Conflict("tournament ${tournamentId.value} has no pga_tournament_id; link it before importing")
        is EspnError.EventNotInScoreboard ->
            DomainError.NotFound("ESPN scoreboard does not contain event $espnEventId for the linked date")
    }

private fun <T> Result<T, EspnError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
