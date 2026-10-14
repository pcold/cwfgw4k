package com.cwfgw.leagues

import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
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
        authenticate(SESSION_AUTH_NAME) {
            post { createLeague(service) }
        }
    }
}

private suspend fun RoutingContext.listLeagues(service: LeagueService) {
    call.respond(service.list())
}

private suspend fun RoutingContext.createLeague(service: LeagueService) {
    requireAdmin()
    val request = call.receive<CreateLeagueRequest>()
    call.respond(HttpStatusCode.Created, service.create(request))
}
