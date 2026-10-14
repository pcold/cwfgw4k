package com.cwfgw.seasons

import com.cwfgw.http.DomainError
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post

/**
 * Kept apart from [seasonRoutes] because [SeasonOpsService] reaches into the scoring and tournament
 * repositories, which the season CRUD wiring does not.
 */
fun Route.seasonOpsRoutes(service: SeasonOpsService) {
    authenticate(SESSION_AUTH_NAME) {
        post("/seasons/{sid}/clean-results") { cleanSeasonResults(service) }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["sid"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.cleanSeasonResults(service: SeasonOpsService) {
    requireAdmin()
    val id = seasonId()
    val cleaned = service.cleanSeasonResults(id) ?: throw DomainError.NotFound("season ${id.value} not found")
    call.respond(cleaned)
}
