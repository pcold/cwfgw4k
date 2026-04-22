package com.cwfgw.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get

fun Route.healthRoutes(probe: HealthProbe) {
    get("/health") { getHealth(probe) }
}

private suspend fun RoutingContext.getHealth(probe: HealthProbe) {
    if (probe.isDatabaseConnected()) {
        call.respond(HealthResponse(status = "ok", service = "cwfgw", database = "connected"))
    } else {
        call.respond(
            HttpStatusCode.InternalServerError,
            HealthResponse(status = "degraded", service = "cwfgw", database = "unreachable"),
        )
    }
}
