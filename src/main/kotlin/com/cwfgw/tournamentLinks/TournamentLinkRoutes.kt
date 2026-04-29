package com.cwfgw.tournamentLinks

import com.cwfgw.espn.EspnError
import com.cwfgw.espn.EspnService
import com.cwfgw.http.DomainError
import com.cwfgw.result.Result
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.toTournamentId
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Admin-only routes for managing manual ESPN→golfer link overrides on a
 * tournament. The GET delegates to ESPN to list every competitor with its
 * current (override-aware) match. The POST/DELETE mutate the overrides
 * stored locally; both return Conflict once the tournament is finalized.
 */
fun Route.tournamentLinkRoutes(
    espnService: EspnService,
    linkService: TournamentLinkService,
) {
    route("/admin/tournaments/{id}") {
        authenticate(SESSION_AUTH_NAME) {
            get("/competitors") { listCompetitors(espnService) }
            post("/player-overrides") { upsertOverride(linkService) }
            delete("/player-overrides/{espnCompetitorId}") { deleteOverride(linkService) }
        }
    }
}

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["id"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private fun RoutingContext.espnCompetitorId(): String =
    call.parameters["espnCompetitorId"]?.takeIf { it.isNotBlank() }
        ?: throw DomainError.Validation("invalid espn competitor id")

private suspend fun RoutingContext.listCompetitors(service: EspnService) {
    requireAdmin()
    call.respond(service.listCompetitorsForLinking(tournamentId()).orThrowEspn())
}

private suspend fun RoutingContext.upsertOverride(service: TournamentLinkService) {
    requireAdmin()
    val request = call.receive<UpsertTournamentPlayerOverrideRequest>()
    call.respond(service.upsert(tournamentId(), request).orThrowLink())
}

private suspend fun RoutingContext.deleteOverride(service: TournamentLinkService) {
    requireAdmin()
    val deleted = service.delete(tournamentId(), espnCompetitorId()).orThrowLink()
    if (!deleted) {
        throw DomainError.NotFound(
            "no player override for tournament ${tournamentId().value} / competitor ${espnCompetitorId()}",
        )
    }
    call.respond(HttpStatusCode.NoContent)
}

private fun TournamentLinkError.toDomainError(): DomainError =
    when (this) {
        is TournamentLinkError.TournamentNotFound -> DomainError.NotFound("tournament ${id.value} not found")
        is TournamentLinkError.TournamentFinalized ->
            DomainError.Conflict("tournament ${id.value} is finalized; player links are locked")
        is TournamentLinkError.GolferNotFound -> DomainError.Validation("golfer ${id.value} not found")
    }

private fun EspnError.toDomainError(): DomainError =
    when (this) {
        is EspnError.UpstreamUnavailable -> DomainError.BadGateway("ESPN returned $status")
        is EspnError.TournamentNotFound -> DomainError.NotFound("tournament ${tournamentId.value} not found")
        is EspnError.TournamentNotLinked ->
            DomainError.Conflict("tournament ${tournamentId.value} has no pga_tournament_id; link it before listing")
        is EspnError.EventNotInScoreboard ->
            DomainError.NotFound("ESPN scoreboard does not contain event $espnEventId for the linked date")
    }

private fun <T> Result<T, TournamentLinkError>.orThrowLink(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }

private fun <T> Result<T, EspnError>.orThrowEspn(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
