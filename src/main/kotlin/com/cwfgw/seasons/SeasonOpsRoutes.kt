package com.cwfgw.seasons

import com.cwfgw.http.DomainError
import com.cwfgw.result.Result
import com.cwfgw.tournaments.Tournament
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Season-scope state-transition routes (finalize, clean-results). Lives
 * separately from [seasonRoutes] because the ops service composes
 * scoring + tournament services and shouldn't be mixed into the basic
 * season CRUD wiring.
 */
fun Route.seasonOpsRoutes(service: SeasonOpsService) {
    authenticate(SESSION_AUTH_NAME) {
        route("/seasons/{sid}") {
            post("/finalize") { finalizeSeason(service) }
            post("/clean-results") { cleanSeasonResults(service) }
        }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["sid"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.finalizeSeason(service: SeasonOpsService) {
    requireAdmin()
    call.respond(service.finalizeSeason(seasonId()).orThrow())
}

private suspend fun RoutingContext.cleanSeasonResults(service: SeasonOpsService) {
    requireAdmin()
    call.respond(service.cleanSeasonResults(seasonId()).orThrow())
}

private fun SeasonOpsError.toDomainError(): DomainError =
    when (this) {
        is SeasonOpsError.SeasonNotFound -> DomainError.NotFound("season ${id.value} not found")
        SeasonOpsError.SeasonHasNoTournaments -> DomainError.Conflict("season has no tournaments to finalize")
        is SeasonOpsError.IncompleteTournaments ->
            DomainError.Conflict("cannot finalize season — incomplete tournaments: " + incomplete.formatNames())
    }

private fun List<Tournament>.formatNames(): String =
    joinToString(", ") { tournament -> "'${tournament.name}' (${tournament.startDate})" }

private fun <T> Result<T, SeasonOpsError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
