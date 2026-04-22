package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.toGolferId
import com.cwfgw.http.DomainError
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.teamRoutes(service: TeamService) {
    route("/seasons/{seasonId}") {
        get("/rosters") { rosterView(service) }
        route("/teams") {
            get { listTeams(service) }
            post { createTeam(service) }
            teamDetailRoutes(service)
        }
    }
}

private fun Route.teamDetailRoutes(service: TeamService) {
    get("/{teamId}") { getTeam(service) }
    put("/{teamId}") { updateTeam(service) }
    route("/{teamId}/roster") {
        get { getRoster(service) }
        post { addToRoster(service) }
        delete("/{golferId}") { dropFromRoster(service) }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["seasonId"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private fun RoutingContext.teamId(): TeamId =
    call.parameters["teamId"]?.toTeamId() ?: throw DomainError.Validation("invalid team id")

private fun RoutingContext.golferId(): GolferId =
    call.parameters["golferId"]?.toGolferId() ?: throw DomainError.Validation("invalid golfer id")

private suspend fun RoutingContext.rosterView(service: TeamService) {
    call.respond(service.getRosterView(seasonId()))
}

private suspend fun RoutingContext.listTeams(service: TeamService) {
    call.respond(service.listBySeason(seasonId()))
}

private suspend fun RoutingContext.createTeam(service: TeamService) {
    val request = call.receive<CreateTeamRequest>()
    call.respond(HttpStatusCode.Created, service.create(seasonId(), request))
}

private suspend fun RoutingContext.getTeam(service: TeamService) {
    val teamId = teamId()
    val team = service.get(teamId) ?: throw DomainError.NotFound("team ${teamId.value} not found")
    call.respond(team)
}

private suspend fun RoutingContext.updateTeam(service: TeamService) {
    val teamId = teamId()
    val request = call.receive<UpdateTeamRequest>()
    val team = service.update(teamId, request) ?: throw DomainError.NotFound("team ${teamId.value} not found")
    call.respond(team)
}

private suspend fun RoutingContext.getRoster(service: TeamService) {
    call.respond(service.getRoster(teamId()))
}

private suspend fun RoutingContext.addToRoster(service: TeamService) {
    val request = call.receive<AddToRosterRequest>()
    call.respond(HttpStatusCode.Created, service.addToRoster(teamId(), request))
}

private suspend fun RoutingContext.dropFromRoster(service: TeamService) {
    val teamId = teamId()
    val golferId = golferId()
    if (!service.dropFromRoster(teamId, golferId)) {
        throw DomainError.NotFound("roster entry for golfer ${golferId.value} on team ${teamId.value} not found")
    }
    call.respond(HttpStatusCode.NoContent)
}
