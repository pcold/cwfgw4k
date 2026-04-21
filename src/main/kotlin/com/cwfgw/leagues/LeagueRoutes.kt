package com.cwfgw.leagues

import com.cwfgw.serialization.toUUIDOrNull
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
            val id = call.parameters["id"]?.toUUIDOrNull()?.let(::LeagueId)
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
