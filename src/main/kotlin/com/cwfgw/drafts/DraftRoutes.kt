package com.cwfgw.drafts

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.users.SESSION_AUTH_NAME
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private const val DEFAULT_DRAFT_ROUNDS = 6

fun Route.draftRoutes(service: DraftService) {
    route("/seasons/{seasonId}/draft") {
        get { getDraft(service) }
        get("/picks") { getPicks(service) }
        get("/available") { getAvailableGolfers(service) }
        authenticate(SESSION_AUTH_NAME) {
            post { createDraft(service) }
            post("/start") { startDraft(service) }
            post("/initialize") { initializeDraft(service) }
            post("/pick") { makePick(service) }
        }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["seasonId"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private fun DraftError.toDomainError(): DomainError =
    when (this) {
        DraftError.NotFound -> DomainError.NotFound("no draft found for this season")
        is DraftError.WrongStatus -> DomainError.Conflict("draft is $current, expected $expected")
        DraftError.AllPicksMade -> DomainError.Conflict("all picks have been made")
        is DraftError.NotYourTurn ->
            DomainError.Conflict(
                "it is team ${actualTeam.value}'s turn to pick, not team ${requestedTeam.value}",
            )
        DraftError.NoTeams -> DomainError.Conflict("season has no teams")
    }

private fun <T> Result<T, DraftError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }

private suspend fun RoutingContext.getDraft(service: DraftService) {
    val draft = service.get(seasonId()) ?: throw DomainError.NotFound("no draft found for this season")
    call.respond(draft)
}

private suspend fun RoutingContext.createDraft(service: DraftService) {
    val request = call.receive<CreateDraftRequest>()
    call.respond(HttpStatusCode.Created, service.create(seasonId(), request))
}

private suspend fun RoutingContext.startDraft(service: DraftService) {
    call.respond(service.start(seasonId()).orThrow())
}

private suspend fun RoutingContext.initializeDraft(service: DraftService) {
    val rounds = optionalQueryParam("rounds", String::toIntOrNull) ?: DEFAULT_DRAFT_ROUNDS
    call.respond(service.initializePicks(seasonId(), rounds).orThrow())
}

private suspend fun RoutingContext.makePick(service: DraftService) {
    val request = call.receive<MakePickRequest>()
    call.respond(service.makePick(seasonId(), request).orThrow())
}

private suspend fun RoutingContext.getPicks(service: DraftService) {
    call.respond(service.getPicks(seasonId()).orThrow())
}

private suspend fun RoutingContext.getAvailableGolfers(service: DraftService) {
    call.respond(service.getAvailableGolfers(seasonId()).orThrow())
}
