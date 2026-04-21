package com.cwfgw.golfers

import com.cwfgw.serialization.toUUIDOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.golferRoutes(service: GolferService) {
    route("/golfers") {
        get {
            val activeOnly = call.request.queryParameters["active"]?.toBooleanStrictOrNull() ?: true
            val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
            call.respond(service.list(activeOnly = activeOnly, search = search))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toUUIDOrNull()?.let(::GolferId)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            when (val golfer = service.get(id)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(golfer)
            }
        }

        post {
            val request = call.receive<CreateGolferRequest>()
            call.respond(HttpStatusCode.Created, service.create(request))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toUUIDOrNull()?.let(::GolferId)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<UpdateGolferRequest>()
            when (val golfer = service.update(id, request)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(golfer)
            }
        }
    }
}
