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
            post("/seasons/{id}/upload/preview") { previewSeasonSchedule(service) }
            post("/seasons/{id}/upload/confirm") { confirmSeasonSchedule(service) }
            post("/roster/preview") { previewRoster(service) }
            post("/roster/confirm") { confirmRoster(service) }
        }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["id"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private suspend fun RoutingContext.previewSeasonSchedule(service: AdminService) {
    requireAdmin()
    val id = seasonId()
    val body = call.receive<UploadSeasonRequest>()
    call.respond(service.previewSeasonSchedule(id, body.startDate, body.endDate).orThrow())
}

private suspend fun RoutingContext.confirmSeasonSchedule(service: AdminService) {
    requireAdmin()
    val id = seasonId()
    val body = call.receive<ConfirmSeasonScheduleRequest>()
    call.respond(service.confirmSeasonSchedule(id, body.entries).orThrow())
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
        is AdminError.UpstreamUnavailable -> DomainError.BadGateway(failure.summary)
        is AdminError.InvalidRoster -> DomainError.Validation(parseError.flattenMessage())
        is AdminError.GolferIdsNotFound ->
            DomainError.Validation("golfer ids not found: ${ids.joinToString(", ") { it.value.toString() }}")
        is AdminError.TeamNameTaken ->
            DomainError.Conflict("a team name in this roster is already taken in season ${seasonId.value}")
        is AdminError.UnresolvableEspnGolfers ->
            DomainError.Unprocessable(
                "ESPN athletes ${espnIds.joinToString(", ")} each name a golfer without an ESPN id that another " +
                    "of them also names; assign each such golfer to at most one of them",
            )
        is AdminError.InvalidConfirmedRoster ->
            DomainError.Validation("invalid roster: " + problems.joinToString("; ") { it.describe() })
    }

private fun ConfirmedRosterProblem.describe(): String =
    when (this) {
        is ConfirmedRosterProblem.RepeatedTeamName -> "team name '$teamName' is used by more than one team"
        is ConfirmedRosterProblem.RepeatedExistingGolfer ->
            "team '$teamName' lists golfer ${golferId.value} more than once"
        is ConfirmedRosterProblem.RepeatedNewGolfer -> "team '$teamName' lists new golfer '$name' more than once"
        is ConfirmedRosterProblem.RepeatedEspnGolfer ->
            "team '$teamName' lists ESPN athlete $espnId more than once"
        is ConfirmedRosterProblem.BlankEspnGolferName -> "team '$teamName' gives ESPN athlete $espnId a blank name"
        is ConfirmedRosterProblem.BlankNewGolferName ->
            "team '$teamName' gives its round $round new golfer a blank name"
        is ConfirmedRosterProblem.RoundOutOfRange ->
            "team '$teamName' has a pick in round $round, outside rounds ${DRAFT_ROUNDS.first}–${DRAFT_ROUNDS.last}"
        is ConfirmedRosterProblem.RepeatedRound -> "team '$teamName' has more than one pick in round $round"
        is ConfirmedRosterProblem.MissingRounds ->
            "team '$teamName' has no pick in " + rounds.joinToString(", ") { round -> "round $round" }
        is ConfirmedRosterProblem.OwnershipPctOutOfRange ->
            "team '$teamName' gives its round $round pick an ownership of $ownershipPct%, outside " +
                "${OWNERSHIP_PCTS.first}–${OWNERSHIP_PCTS.last}%"
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
