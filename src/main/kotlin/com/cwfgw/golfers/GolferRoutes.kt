package com.cwfgw.golfers

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.golferRoutes(service: GolferService) {
    route("/golfers") {
        get { listGolfers(service) }
    }
}

private suspend fun RoutingContext.listGolfers(service: GolferService) {
    val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
    call.respond(service.listLinkable(search = search))
}
