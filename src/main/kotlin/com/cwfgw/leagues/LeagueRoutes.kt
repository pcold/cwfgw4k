package com.cwfgw.leagues

import com.cwfgw.http.DomainError
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.leagueRoutes(service: LeagueService) {
    route("/leagues") {
        get {
            call.respond(service.list())
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toLeagueId() ?: throw DomainError.Validation("invalid league id")
            val league = service.get(id) ?: throw DomainError.NotFound("league ${id.value} not found")
            call.respond(league)
        }

        post {
            val request = call.receive<CreateLeagueRequest>()
            call.respond(HttpStatusCode.Created, service.create(request))
        }
    }
}
