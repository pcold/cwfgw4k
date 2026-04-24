package com.cwfgw.espn

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.result.Result
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.toTournamentId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger {}

fun Route.espnRoutes(service: EspnImportService) {
    route("/espn/import") {
        post { importByDate(service) }
        post("/tournament/{tournamentId}") { importForTournament(service) }
    }
}

private suspend fun RoutingContext.importByDate(service: EspnImportService) {
    val date =
        optionalQueryParam("date", ::parseLocalDate)
            ?: throw DomainError.Validation("missing query parameter: date")
    call.respond(service.importByDate(date).orThrow())
}

private suspend fun RoutingContext.importForTournament(service: EspnImportService) {
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
