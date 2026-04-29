package com.cwfgw.tournaments

import com.cwfgw.http.DomainError
import com.cwfgw.result.Result
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Per-tournament state-transition routes (finalize, reset). Lives in
 * its own file rather than [tournamentRoutes] because the ops service
 * composes ESPN + scoring and shouldn't be mixed into the basic
 * tournament CRUD wiring. Season-scope ops live in
 * [com.cwfgw.seasons.seasonOpsRoutes].
 */
fun Route.tournamentOpsRoutes(service: TournamentOpsService) {
    authenticate(SESSION_AUTH_NAME) {
        route("/tournaments/{id}") {
            post("/finalize") { finalizeTournament(service) }
            post("/reset") { resetTournament(service) }
        }
    }
}

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["id"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private suspend fun RoutingContext.finalizeTournament(service: TournamentOpsService) {
    requireAdmin()
    call.respond(service.finalizeTournament(tournamentId()).orThrow())
}

private suspend fun RoutingContext.resetTournament(service: TournamentOpsService) {
    requireAdmin()
    call.respond(service.resetTournament(tournamentId()).orThrow())
}

private fun TournamentOpsError.toDomainError(): DomainError =
    when (this) {
        is TournamentOpsError.TournamentNotFound -> DomainError.NotFound("tournament ${id.value} not found")
        is TournamentOpsError.OutOfOrder -> DomainError.Conflict(outOfOrderMessage())
        is TournamentOpsError.UpstreamUnavailable -> DomainError.BadGateway("ESPN returned $status")
        is TournamentOpsError.EventNotInScoreboard ->
            DomainError.NotFound("ESPN scoreboard does not contain event $espnEventId for the linked date")
    }

private fun TournamentOpsError.OutOfOrder.outOfOrderMessage(): String {
    val verb = if (action == TournamentOpsError.Action.Finalize) "finalize" else "reset"
    return "cannot $verb out of order — these tournaments must be handled first: " + blocking.formatNames()
}

private fun List<Tournament>.formatNames(): String =
    joinToString(", ") { tournament -> "'${tournament.name}' (${tournament.startDate})" }

private fun <T> Result<T, TournamentOpsError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
