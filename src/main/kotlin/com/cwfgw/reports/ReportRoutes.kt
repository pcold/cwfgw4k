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
import io.ktor.server.response.respond
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
    cachedRespond(cache) { service.getSeasonReport(sid).orThrow() }
}

private suspend fun RoutingContext.getReport(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val tid = tournamentId()
    respondReport(service, sid, cache) { live -> service.getReport(sid, tid, live = live).orThrow() }
}

private suspend fun RoutingContext.getRankings(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val through = optionalQueryParam("through", String::toTournamentId)
    respondRollup(service, sid, cache, through) { live ->
        service.getRankings(sid, throughTournamentId = through, live = live).orThrow()
    }
}

private suspend fun RoutingContext.getPlayerRankings(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val through = optionalQueryParam("through", String::toTournamentId)
    respondRollup(service, sid, cache, through) { live ->
        service.getPlayerRankings(sid, throughTournamentId = through, live = live).orThrow()
    }
}

private suspend fun RoutingContext.getGolferHistory(
    service: WeeklyReportService,
    cache: RequestCache,
) {
    val sid = seasonId()
    val gid = golferId()
    cachedRespond(cache) { service.getGolferHistory(sid, gid).orThrow() }
}

// A live body must never sit in the response cache: its TTL would stack on the ESPN cache's, and a fallback
// built while ESPN was failing would be replayed after ESPN recovered. Off-window nothing is overlaid, so it
// caches — under its own URI, since sharing the non-live entry would assume the service builds both alike.
private suspend inline fun <reified T : Any> RoutingContext.respondReport(
    service: WeeklyReportService,
    seasonId: SeasonId,
    cache: RequestCache,
    crossinline build: suspend (live: Boolean) -> T,
) {
    val live = optionalQueryParam("live", String::toBooleanStrictOrNull) ?: false
    val mayOverlay = live && service.hasLiveCandidate(seasonId)
    if (mayOverlay) call.respond(build(live)) else cachedRespond(cache) { build(live) }
}

// The service never overlays a rollup with no `through`, so a `live` flag on one must not cost it the cache.
private suspend inline fun <reified T : Any> RoutingContext.respondRollup(
    service: WeeklyReportService,
    seasonId: SeasonId,
    cache: RequestCache,
    through: TournamentId?,
    crossinline build: suspend (live: Boolean) -> T,
) {
    if (through == null) cachedRespond(cache) { build(false) } else respondReport(service, seasonId, cache, build)
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
