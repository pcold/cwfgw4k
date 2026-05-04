package com.cwfgw.reports

import com.cwfgw.cache.RequestCache
import com.cwfgw.cache.cachedRespond
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.toGolferId
import com.cwfgw.http.DomainError
import com.cwfgw.http.optionalQueryParam
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.toSeasonId
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.toTournamentId
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.reportRoutes(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    route("/seasons/{id}") {
        get("/report") { getSeasonReport(service, cache) }
        get("/report/{tournamentId}") { getReport(service, cache) }
        get("/rankings") { getRankings(service, cache) }
        get("/player-rankings") { getPlayerRankings(service, cache) }
        get("/golfer/{golferId}/history") { getGolferHistory(service, cache) }
    }
}

private fun RoutingContext.seasonId(): SeasonId =
    call.parameters["id"]?.toSeasonId() ?: throw DomainError.Validation("invalid season id")

private fun RoutingContext.tournamentId(): TournamentId =
    call.parameters["tournamentId"]?.toTournamentId() ?: throw DomainError.Validation("invalid tournament id")

private fun RoutingContext.golferId(): GolferId =
    call.parameters["golferId"]?.toGolferId() ?: throw DomainError.Validation("invalid golfer id")

private suspend fun RoutingContext.getSeasonReport(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val live = optionalQueryParam("live", String::toBooleanStrictOrNull) ?: false
    cachedRespond(cache) { service.getSeasonReport(sid, live = live).orThrow() }
}

private suspend fun RoutingContext.getReport(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val tid = tournamentId()
    val live = optionalQueryParam("live", String::toBooleanStrictOrNull) ?: false
    cachedRespond(cache) { service.getReport(sid, tid, live = live).orThrow() }
}

private suspend fun RoutingContext.getRankings(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val live = optionalQueryParam("live", String::toBooleanStrictOrNull) ?: false
    val through = optionalQueryParam("through", String::toTournamentId)
    cachedRespond(cache) { service.getRankings(sid, throughTournamentId = through, live = live).orThrow() }
}

private suspend fun RoutingContext.getPlayerRankings(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val live = optionalQueryParam("live", String::toBooleanStrictOrNull) ?: false
    val through = optionalQueryParam("through", String::toTournamentId)
    cachedRespond(cache) { service.getPlayerRankings(sid, throughTournamentId = through, live = live).orThrow() }
}

private suspend fun RoutingContext.getGolferHistory(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val gid = golferId()
    cachedRespond(cache) { service.getGolferHistory(sid, gid).orThrow() }
}

private fun ReportError.toDomainError(): DomainError =
    when (this) {
        is ReportError.SeasonNotFound -> DomainError.NotFound("season ${seasonId.value} not found")
        is ReportError.TournamentNotFound -> DomainError.NotFound("tournament ${tournamentId.value} not found")
        is ReportError.GolferNotFound -> DomainError.NotFound("golfer ${golferId.value} not found")
    }

private fun <T> Result<T, ReportError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
