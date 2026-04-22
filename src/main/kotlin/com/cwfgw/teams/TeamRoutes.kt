package com.cwfgw.teams

import com.cwfgw.golfers.toGolferId
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

private suspend fun RoutingContext.rosterView(service: TeamService) {
    val seasonId =
        call.parameters["seasonId"]?.toSeasonId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    call.respond(service.getRosterView(seasonId))
}

private suspend fun RoutingContext.listTeams(service: TeamService) {
    val seasonId =
        call.parameters["seasonId"]?.toSeasonId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    call.respond(service.listBySeason(seasonId))
}

private suspend fun RoutingContext.createTeam(service: TeamService) {
    val seasonId =
        call.parameters["seasonId"]?.toSeasonId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    val request = call.receive<CreateTeamRequest>()
    call.respond(HttpStatusCode.Created, service.create(seasonId, request))
}

private suspend fun RoutingContext.getTeam(service: TeamService) {
    val teamId =
        call.parameters["teamId"]?.toTeamId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    when (val team = service.get(teamId)) {
        null -> call.respond(HttpStatusCode.NotFound)
        else -> call.respond(team)
    }
}

private suspend fun RoutingContext.updateTeam(service: TeamService) {
    val teamId =
        call.parameters["teamId"]?.toTeamId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    val request = call.receive<UpdateTeamRequest>()
    when (val team = service.update(teamId, request)) {
        null -> call.respond(HttpStatusCode.NotFound)
        else -> call.respond(team)
    }
}

private suspend fun RoutingContext.getRoster(service: TeamService) {
    val teamId =
        call.parameters["teamId"]?.toTeamId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    call.respond(service.getRoster(teamId))
}

private suspend fun RoutingContext.addToRoster(service: TeamService) {
    val teamId =
        call.parameters["teamId"]?.toTeamId()
            ?: return call.respond(HttpStatusCode.BadRequest)
    val request = call.receive<AddToRosterRequest>()
    call.respond(HttpStatusCode.Created, service.addToRoster(teamId, request))
}

private suspend fun RoutingContext.dropFromRoster(service: TeamService) {
    val teamId = call.parameters["teamId"]?.toTeamId()
    val golferId = call.parameters["golferId"]?.toGolferId()
    if (teamId == null || golferId == null) {
        return call.respond(HttpStatusCode.BadRequest)
    }
    if (service.dropFromRoster(teamId, golferId)) {
        call.respond(HttpStatusCode.NoContent)
    } else {
        call.respond(HttpStatusCode.NotFound)
    }
}
