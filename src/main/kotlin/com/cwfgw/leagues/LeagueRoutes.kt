package com.cwfgw.leagues

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.leagueRoutes(service: LeagueService) {
    route("/leagues") {
        get {
            call.respond(service.list())
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toLeagueIdOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            when (val league = service.get(id)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(league)
            }
        }

        post {
            val request = call.receive<CreateLeagueRequest>()
            call.respond(HttpStatusCode.Created, service.create(request))
        }
    }
}

private fun String.toLeagueIdOrNull(): LeagueId? =
    try {
        LeagueId(UUID.fromString(this))
    } catch (_: IllegalArgumentException) {
        null
    }
