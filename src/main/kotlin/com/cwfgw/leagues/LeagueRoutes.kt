package com.cwfgw.leagues

import com.cwfgw.http.DomainError
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.leagueRoutes(service: LeagueService) {
    route("/leagues") {
        get { listLeagues(service) }
        get("/{id}") { getLeague(service) }
        post { createLeague(service) }
    }
}

private fun RoutingContext.leagueId(): LeagueId =
    call.parameters["id"]?.toLeagueId() ?: throw DomainError.Validation("invalid league id")

private suspend fun RoutingContext.listLeagues(service: LeagueService) {
    call.respond(service.list())
}

private suspend fun RoutingContext.getLeague(service: LeagueService) {
    val id = leagueId()
    val league = service.get(id) ?: throw DomainError.NotFound("league ${id.value} not found")
    call.respond(league)
}

private suspend fun RoutingContext.createLeague(service: LeagueService) {
    val request = call.receive<CreateLeagueRequest>()
    call.respond(HttpStatusCode.Created, service.create(request))
}
