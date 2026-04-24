package com.cwfgw.tournaments

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.users.SESSION_AUTH_NAME
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.tournamentRoutes(service: TournamentService) {
    route("/tournaments") {
        get { listTournaments(service) }
        get("/{id}") { getTournament(service) }
        get("/{id}/results") { getResults(service) }
        authenticate(SESSION_AUTH_NAME) {
            post { createTournament(service) }
            put("/{id}") { updateTournament(service) }
            post("/{id}/results") { importResults(service) }
        }
    }
}

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["id"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private suspend fun RoutingContext.listTournaments(service: TournamentService) {
    val seasonId = optionalQueryParam("season_id", String::toSeasonId)
    val status = call.request.queryParameters["status"]?.takeIf { it.isNotBlank() }
    call.respond(service.list(seasonId = seasonId, status = status))
}

private suspend fun RoutingContext.getTournament(service: TournamentService) {
    val id = tournamentId()
    val tournament = service.get(id) ?: throw DomainError.NotFound("tournament ${id.value} not found")
    call.respond(tournament)
}

private suspend fun RoutingContext.createTournament(service: TournamentService) {
    val request = call.receive<CreateTournamentRequest>()
    call.respond(HttpStatusCode.Created, service.create(request))
}

private suspend fun RoutingContext.updateTournament(service: TournamentService) {
    val id = tournamentId()
    val request = call.receive<UpdateTournamentRequest>()
    val tournament =
        service.update(id, request) ?: throw DomainError.NotFound("tournament ${id.value} not found")
    call.respond(tournament)
}

private suspend fun RoutingContext.getResults(service: TournamentService) {
    call.respond(service.getResults(tournamentId()))
}

private suspend fun RoutingContext.importResults(service: TournamentService) {
    val id = tournamentId()
    val requests = call.receive<List<CreateTournamentResultRequest>>()
    call.respond(service.importResults(id, requests))
}
