package com.cwfgw.seasons

import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.leagues.toLeagueId
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

fun Route.seasonRoutes(service: SeasonService) {
    route("/seasons") {
        get { listSeasons(service) }
        get("/{id}/rules") { getRules(service) }
        authenticate(SESSION_AUTH_NAME) {
            get("/default-rules") { getDefaultRules() }
            post { createSeason(service) }
            delete("/{id}") { deleteSeason(service) }
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

private suspend fun RoutingContext.createSeason(service: SeasonService) {
    requireAdmin()
    val request = call.receive<CreateSeasonRequest>()
    request.violation()?.let { throw it.toDomainError() }
    call.respond(HttpStatusCode.Created, service.create(request))
}

private fun CreateSeasonViolation.toDomainError(): DomainError =
    when (this) {
        CreateSeasonViolation.NoPayoutPlaces -> DomainError.Validation("payouts must list at least one place")
        CreateSeasonViolation.NoSideBetRounds -> DomainError.Validation("side bet rounds must list at least one round")
        CreateSeasonViolation.DuplicateSideBetRound -> DomainError.Validation("side bet rounds must not repeat a round")
        CreateSeasonViolation.BlankName -> DomainError.Validation("season name must not be blank")
        CreateSeasonViolation.MaxTeamsBelowOne -> DomainError.Validation("max teams must be at least 1")
        is CreateSeasonViolation.RuleAmount -> DomainError.Validation(message())
    }

private fun CreateSeasonViolation.RuleAmount.message(): String =
    when (this) {
        CreateSeasonViolation.NegativeSideBetAmount -> "side bet amount must not be negative"
        CreateSeasonViolation.NegativePayout -> "payouts must not be negative"
        CreateSeasonViolation.NegativeTieFloor -> "tie floor must not be negative"
        CreateSeasonViolation.PayoutTooLarge -> "payouts must be less than 1,000,000"
        CreateSeasonViolation.TieFloorTooLarge -> "tie floor must be less than 1,000,000"
        CreateSeasonViolation.SideBetAmountTooLarge -> "side bet amount must be less than 1,000,000"
        CreateSeasonViolation.PayoutTooManyDecimalPlaces -> "payouts must have at most 4 decimal places"
        CreateSeasonViolation.TieFloorTooManyDecimalPlaces -> "tie floor must have at most 4 decimal places"
        CreateSeasonViolation.SideBetAmountTooManyDecimalPlaces -> "side bet amount must have at most 4 decimal places"
    }

private suspend fun RoutingContext.getRules(service: SeasonService) {
    val id = seasonId()
    val rules = service.getRules(id) ?: throw DomainError.NotFound("season ${id.value} not found")
    call.respond(rules)
}

private suspend fun RoutingContext.getDefaultRules() {
    requireAdmin()
    call.respond(SeasonRules.defaults())
}

private suspend fun RoutingContext.deleteSeason(service: SeasonService) {
    requireAdmin()
    val id = seasonId()
    if (!service.delete(id)) throw DomainError.NotFound("season ${id.value} not found")
    call.respond(HttpStatusCode.NoContent)
}
