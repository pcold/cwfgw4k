package com.cwfgw.golfers

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
import io.ktor.server.routing.route

fun Route.golferRoutes(service: GolferService) {
    route("/golfers") {
        get { listGolfers(service) }
        authenticate(SESSION_AUTH_NAME) {
            post { createGolfer(service) }
        }
    }
}

private suspend fun RoutingContext.listGolfers(service: GolferService) {
    val activeOnly = optionalQueryParam("active", String::toBooleanStrictOrNull) ?: true
    val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
    call.respond(service.list(activeOnly = activeOnly, search = search))
}

private suspend fun RoutingContext.createGolfer(service: GolferService) {
    requireAdmin()
    val request = call.receive<CreateGolferRequest>()
    call.respond(HttpStatusCode.Created, service.create(request))
}
