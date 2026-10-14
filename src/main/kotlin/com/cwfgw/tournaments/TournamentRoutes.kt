package com.cwfgw.tournaments

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.tournamentRoutes(service: TournamentService) {
    route("/tournaments") {
        get { listTournaments(service) }
        authenticate(SESSION_AUTH_NAME) {
            put("/{id}") { updateTournament(service) }
        }
    }
}

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["id"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private suspend fun RoutingContext.listTournaments(service: TournamentService) {
    val seasonId = optionalQueryParam("season_id", String::toSeasonId)
    val status = optionalQueryParam("status", TournamentStatus::fromValueOrNull)
    call.respond(service.list(seasonId = seasonId, status = status))
}

private suspend fun RoutingContext.updateTournament(service: TournamentService) {
    requireAdmin()
    val id = tournamentId()
    val request = call.receive<UpdateTournamentRequest>()
    val tournament =
        service.update(id, request) ?: throw DomainError.NotFound("tournament ${id.value} not found")
    call.respond(tournament)
}
