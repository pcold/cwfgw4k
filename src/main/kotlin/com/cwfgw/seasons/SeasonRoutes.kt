package com.cwfgw.seasons

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.leagues.toLeagueId
import com.cwfgw.users.SESSION_AUTH_NAME
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

fun Route.seasonRoutes(service: SeasonService) {
    route("/seasons") {
        get { listSeasons(service) }
        get("/{id}") { getSeason(service) }
        get("/{id}/rules") { getRules(service) }
        authenticate(SESSION_AUTH_NAME) {
            post { createSeason(service) }
            put("/{id}") { updateSeason(service) }
        }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["id"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.listSeasons(service: SeasonService) {
    val leagueId = optionalQueryParam("league_id", String::toLeagueId)
    val year = optionalQueryParam("year", String::toIntOrNull)
    call.respond(service.list(leagueId = leagueId, seasonYear = year))
}

private suspend fun RoutingContext.getSeason(service: SeasonService) {
    val id = seasonId()
    val season = service.get(id) ?: throw DomainError.NotFound("season ${id.value} not found")
    call.respond(season)
}

private suspend fun RoutingContext.createSeason(service: SeasonService) {
    val request = call.receive<CreateSeasonRequest>()
    call.respond(HttpStatusCode.Created, service.create(request))
}

private suspend fun RoutingContext.updateSeason(service: SeasonService) {
    val id = seasonId()
    val request = call.receive<UpdateSeasonRequest>()
    val season = service.update(id, request) ?: throw DomainError.NotFound("season ${id.value} not found")
    call.respond(season)
}

private suspend fun RoutingContext.getRules(service: SeasonService) {
    val id = seasonId()
    val rules = service.getRules(id) ?: throw DomainError.NotFound("season ${id.value} not found")
    call.respond(rules)
}
