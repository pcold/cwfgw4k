package com.cwfgw.admin

import com.cwfgw.http.DomainError
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.adminRoutes(service: AdminService) {
    route("/admin") {
        authenticate(SESSION_AUTH_NAME) {
            post("/seasons/{id}/upload") { uploadSeason(service) }
            post("/roster/preview") { previewRoster(service) }
            post("/roster/confirm") { confirmRoster(service) }
        }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["id"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.uploadSeason(service: AdminService) {
    requireAdmin()
    val id = seasonId()
    val body = call.receive<UploadSeasonRequest>()
    call.respond(service.uploadSeason(id, body.startDate, body.endDate).orThrow())
}

private suspend fun RoutingContext.previewRoster(service: AdminService) {
    requireAdmin()
    val tsv = call.receiveText()
    call.respond(service.previewRoster(tsv).orThrow())
}

private suspend fun RoutingContext.confirmRoster(service: AdminService) {
    requireAdmin()
    val request = call.receive<ConfirmRosterRequest>()
    call.respond(service.confirmRoster(request).orThrow())
}

private fun AdminError.toDomainError(): DomainError =
    when (this) {
        is AdminError.SeasonNotFound -> DomainError.NotFound("season ${seasonId.value} not found")
        is AdminError.UpstreamUnavailable -> DomainError.BadGateway("ESPN returned $status")
        is AdminError.InvalidRoster -> DomainError.Validation(parseError.flattenMessage())
        is AdminError.GolferIdsNotFound ->
            DomainError.Validation("golfer ids not found: ${ids.joinToString(", ") { it.value.toString() }}")
    }

private fun RosterParseError.flattenMessage(): String =
    when (this) {
        is RosterParseError.InvalidHeader -> message
        is RosterParseError.InvalidRows ->
            "roster rows failed to parse: " + errors.joinToString("; ") { "row ${it.rowNumber}: ${it.message}" }
    }

private fun <T> Result<T, AdminError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
