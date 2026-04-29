package com.cwfgw.scoring

import com.cwfgw.http.DomainError
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.toTournamentId
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.requireAdmin
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.scoringRoutes(service: ScoringService) {
    route("/seasons/{seasonId}") {
        get("/standings") { getStandings(service) }
        route("/scoring") {
            get("/side-bets") { getSideBetStandings(service) }
            get("/{tournamentId}") { getScores(service) }
            authenticate(SESSION_AUTH_NAME) {
                post("/refresh-standings") { refreshStandings(service) }
                post("/calculate/{tournamentId}") { calculateScores(service) }
            }
        }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["seasonId"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["tournamentId"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private fun ScoringError.toDomainError(): DomainError =
    when (this) {
        ScoringError.SeasonNotFound -> DomainError.NotFound("season not found")
        ScoringError.TournamentNotFound -> DomainError.NotFound("tournament not found")
        ScoringError.NoTeams -> DomainError.Conflict("season has no teams")
    }

private fun <T> Result<T, ScoringError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }

private suspend fun RoutingContext.getStandings(service: ScoringService) {
    call.respond(service.getStandings(seasonId()))
}

private suspend fun RoutingContext.getScores(service: ScoringService) {
    call.respond(service.getScores(seasonId(), tournamentId()))
}

private suspend fun RoutingContext.calculateScores(service: ScoringService) {
    requireAdmin()
    call.respond(service.calculateScores(seasonId(), tournamentId()).orThrow())
}

private suspend fun RoutingContext.refreshStandings(service: ScoringService) {
    requireAdmin()
    call.respond(service.refreshStandings(seasonId()).orThrow())
}

private suspend fun RoutingContext.getSideBetStandings(service: ScoringService) {
    call.respond(service.getSideBetStandings(seasonId()).orThrow())
}
