package com.cwfgw.espn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

private val DATE_A = LocalDate.parse("2026-04-09")
private val DATE_B = LocalDate.parse("2026-04-16")

private fun event(id: String): EspnTournament =
    EspnTournament(
        espnId = id,
        name = "Event $id",
        completed = true,
        competitors = emptyList(),
        isTeamEvent = false,
    )

/**
 * Counts delegate invocations so the cache's hit / miss behavior is
 * observable from the spec.
 */
private class CountingEspnClient(
    private val behaviour: (LocalDate) -> List<EspnTournament> = { listOf(event("e-$it")) },
) : EspnClient {
    val scoreboardCalls = AtomicInteger(0)
    val calendarCalls = AtomicInteger(0)

    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        scoreboardCalls.incrementAndGet()
        return behaviour(date)
    }

    override suspend fun fetchCalendar(): List<EspnCalendarEntry> {
        calendarCalls.incrementAndGet()
        return emptyList()
    }
}

class CachingEspnClientSpec : FunSpec({

    test("fetchScoreboard caches the response and only delegates once for the same date") {
        val delegate = CountingEspnClient()
        val client = CachingEspnClient(delegate, scoreboardTtl = Duration.ofMinutes(5), scoreboardMaxSize = 256)

        val first = client.fetchScoreboard(DATE_A)
        val second = client.fetchScoreboard(DATE_A)

        first shouldBe second
        delegate.scoreboardCalls.get() shouldBe 1
    }

    test("fetchScoreboard misses independently for different dates") {
        val delegate = CountingEspnClient()
        val client = CachingEspnClient(delegate, scoreboardTtl = Duration.ofMinutes(5), scoreboardMaxSize = 256)

        client.fetchScoreboard(DATE_A)
        client.fetchScoreboard(DATE_B)
        client.fetchScoreboard(DATE_A)
        client.fetchScoreboard(DATE_B)

        delegate.scoreboardCalls.get() shouldBe 2
    }

    test("upstream exceptions are not cached — the next call retries the delegate") {
        val callCount = AtomicInteger(0)
        val delegate =
            CountingEspnClient { _ ->
                if (callCount.incrementAndGet() == 1) {
                    throw EspnUpstreamException(status = 503, message = "Service Unavailable")
                }
                listOf(event("e-recovered"))
            }
        val client = CachingEspnClient(delegate, scoreboardTtl = Duration.ofMinutes(5), scoreboardMaxSize = 256)

        shouldThrow<EspnUpstreamException> { client.fetchScoreboard(DATE_A) }
        val recovered = client.fetchScoreboard(DATE_A)

        recovered shouldBe listOf(event("e-recovered"))
        delegate.scoreboardCalls.get() shouldBe 2
    }

    test("entries expire after the configured TTL") {
        val delegate = CountingEspnClient()
        val client =
            CachingEspnClient(delegate, scoreboardTtl = Duration.ofMillis(50), scoreboardMaxSize = 256)

        client.fetchScoreboard(DATE_A)
        Thread.sleep(EXPIRY_WAIT_MS)
        client.fetchScoreboard(DATE_A)

        delegate.scoreboardCalls.get() shouldBe 2
    }

    test("fetchCalendar is cached too — repeat calls dedupe") {
        val delegate = CountingEspnClient()
        val client = CachingEspnClient(delegate, scoreboardTtl = Duration.ofMinutes(5), scoreboardMaxSize = 256)

        client.fetchCalendar()
        client.fetchCalendar()
        client.fetchCalendar()

        delegate.calendarCalls.get() shouldBe 1
    }

    test("concurrent fetches for the same date dedupe to a single delegate call (single-flight)") {
        // The delegate's first call blocks on a gate so we can fan out N
        // concurrent callers before the load resolves. Without single-flight
        // they'd all see no cache entry and fan out N delegate calls.
        val gate = CompletableDeferred<List<EspnTournament>>()
        val callsStarted = AtomicInteger(0)
        val delegate =
            object : EspnClient {
                override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
                    callsStarted.incrementAndGet()
                    return gate.await()
                }

                override suspend fun fetchCalendar(): List<EspnCalendarEntry> = emptyList()
            }
        val client =
            CachingEspnClient(delegate, scoreboardTtl = Duration.ofMinutes(5), scoreboardMaxSize = 256)

        val results =
            coroutineScope {
                val concurrent = (1..10).map { async { client.fetchScoreboard(DATE_A) } }
                // Give Caffeine a beat to register all 10 callers as awaiters
                // on the same in-flight future before we resolve the gate.
                Thread.sleep(SETTLE_MS)
                gate.complete(listOf(event("e-shared")))
                concurrent.awaitAll()
            }

        results.size shouldBe 10
        results.forEach { it shouldBe listOf(event("e-shared")) }
        callsStarted.get() shouldBe 1
    }
}) {
    companion object {
        // Slop above Caffeine's expireAfterWrite TTL so the second fetch reliably misses.
        private const val EXPIRY_WAIT_MS: Long = 200

        // Brief pause to let all concurrent callers register as awaiters on
        // the AsyncCache's in-flight future before the gate resolves.
        private const val SETTLE_MS: Long = 50
    }
}
