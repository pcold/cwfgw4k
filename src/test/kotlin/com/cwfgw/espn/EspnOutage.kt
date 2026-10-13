package com.cwfgw.espn

import com.cwfgw.config.EspnConfig
import com.cwfgw.seasons.SeasonId
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.time.Duration

private val OUTAGE_ESPN_CONFIG = EspnConfig(scoreboardCacheTtlSeconds = 60, scoreboardCacheMaxSize = 8)
private const val OUTAGE_BASE_URL = "https://espn.test/scoreboard"

// Linked to an ESPN event: `listCompetitorsForLinking` answers an unlinked tournament before it asks ESPN.
// In play, never Completed: a live report does not ask ESPN about a completed tournament.
internal val OUTAGE_TOURNAMENT: Tournament =
    Tournament(
        id = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01")),
        pgaTournamentId = "espn-masters",
        name = "The Masters",
        seasonId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa")),
        startDate = LocalDate.parse("2026-04-09"),
        endDate = LocalDate.parse("2026-04-12"),
        courseName = null,
        status = TournamentStatus.InProgress,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

/**
 * The production client, cache over HTTP, with only the engine replaced by [handler]. No timer runs unless
 * [requestTimeout] is given: under one, a stall on a loaded machine turns an answered request into a timeout.
 */
internal fun espnClientOver(
    requestTimeout: Duration? = null,
    handler: MockRequestHandler,
): EspnClient =
    EspnClient(
        httpClient =
            HttpClient(MockEngine(handler)) {
                if (requestTimeout != null) {
                    install(HttpTimeout) { requestTimeoutMillis = requestTimeout.inWholeMilliseconds }
                }
            },
        config = OUTAGE_ESPN_CONFIG,
        baseUrl = OUTAGE_BASE_URL,
    )

/**
 * Each throws what a real client was observed to raise for that failure, which a mock engine cannot produce
 * by itself. [TimesOut] throws it too, so that no case here waits on a timer.
 */
internal enum class EspnOutage(val description: String, val raises: KClass<out Exception>) {
    TimesOut("times out", HttpRequestTimeoutException::class),
    CannotBeReached("cannot be reached", ConnectException::class),
    DoesNotResolve("does not resolve", UnresolvedAddressException::class),
    ;

    /**
     * [requests] counts what reached the engine, which is how a spec shows ESPN was asked at all. Given
     * [recovered], only the first request fails and every later one answers 200 with that body.
     */
    fun client(
        requests: AtomicInteger = AtomicInteger(),
        recovered: String? = null,
    ): EspnClient =
        espnClientOver { request ->
            if (requests.incrementAndGet() == 1 || recovered == null) fail(request)
            respond(content = recovered, status = HttpStatusCode.OK)
        }

    private fun fail(request: HttpRequestData): Nothing =
        when (this) {
            TimesOut -> throw HttpRequestTimeoutException(request)
            CannotBeReached -> throw ConnectException("Connection refused")
            DoesNotResolve -> throw UnresolvedAddressException()
        }
}
