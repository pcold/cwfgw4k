package com.cwfgw.teams

import com.cwfgw.http.DomainError
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get

fun Route.teamRoutes(service: TeamService) {
    get("/seasons/{seasonId}/rosters") { rosterView(service) }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["seasonId"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.rosterView(service: TeamService) {
    call.respond(service.getRosterView(seasonId()))
}
