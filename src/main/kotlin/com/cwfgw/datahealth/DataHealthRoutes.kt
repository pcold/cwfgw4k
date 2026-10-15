package com.cwfgw.datahealth

import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.dataHealthRoutes(service: DataHealthService) {
    route("/admin") {
        authenticate(SESSION_AUTH_NAME) {
            get("/data-health") { dataHealthReport(service) }
        }
    }
}

private suspend fun RoutingContext.dataHealthReport(service: DataHealthService) {
    requireAdmin()
    call.respond(service.report())
}
