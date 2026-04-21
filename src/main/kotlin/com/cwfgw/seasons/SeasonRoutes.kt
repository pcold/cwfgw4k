package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId
import com.cwfgw.serialization.toUUIDOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.seasonRoutes(service: SeasonService) {
    route("/seasons") {
        get {
            val leagueId = call.request.queryParameters["league_id"]?.toUUIDOrNull()?.let(::LeagueId)
            val year = call.request.queryParameters["year"]?.toIntOrNull()
            call.respond(service.list(leagueId = leagueId, seasonYear = year))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toUUIDOrNull()?.let(::SeasonId)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            when (val season = service.get(id)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(season)
            }
        }

        post {
            val request = call.receive<CreateSeasonRequest>()
            call.respond(HttpStatusCode.Created, service.create(request))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toUUIDOrNull()?.let(::SeasonId)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<UpdateSeasonRequest>()
            when (val season = service.update(id, request)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(season)
            }
        }

        get("/{id}/rules") {
            val id = call.parameters["id"]?.toUUIDOrNull()?.let(::SeasonId)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            when (val rules = service.getRules(id)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(rules)
            }
        }
    }
}
