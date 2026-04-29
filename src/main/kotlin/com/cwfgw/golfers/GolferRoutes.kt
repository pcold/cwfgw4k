package com.cwfgw.golfers

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
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
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.golferRoutes(service: GolferService) {
    route("/golfers") {
        get { listGolfers(service) }
        get("/{id}") { getGolfer(service) }
        authenticate(SESSION_AUTH_NAME) {
            post { createGolfer(service) }
            put("/{id}") { updateGolfer(service) }
        }
    }
}

private fun RoutingContext.golferId(): GolferId =
    call.parameters["id"]?.toGolferId() ?: throw DomainError.Validation("invalid golfer id")

private suspend fun RoutingContext.listGolfers(service: GolferService) {
    val activeOnly = optionalQueryParam("active", String::toBooleanStrictOrNull) ?: true
    val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
    call.respond(service.list(activeOnly = activeOnly, search = search))
}

private suspend fun RoutingContext.getGolfer(service: GolferService) {
    val id = golferId()
    val golfer = service.get(id) ?: throw DomainError.NotFound("golfer ${id.value} not found")
    call.respond(golfer)
}

private suspend fun RoutingContext.createGolfer(service: GolferService) {
    requireAdmin()
    val request = call.receive<CreateGolferRequest>()
    call.respond(HttpStatusCode.Created, service.create(request))
}

private suspend fun RoutingContext.updateGolfer(service: GolferService) {
    requireAdmin()
    val id = golferId()
    val request = call.receive<UpdateGolferRequest>()
    val golfer = service.update(id, request) ?: throw DomainError.NotFound("golfer ${id.value} not found")
    call.respond(golfer)
}
