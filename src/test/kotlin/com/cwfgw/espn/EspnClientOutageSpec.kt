package com.cwfgw.espn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private val DATE: LocalDate = LocalDate.parse("2026-04-09")

private const val RECOVERED_SCOREBOARD =
    """{"events":[{"id":"espn-masters","name":"The Masters","competitions":[{"competitors":[]}]}]}"""

class EspnClientOutageSpec : FunSpec({

    EspnOutage.entries.forEach { outage ->
        test("fetchScoreboard reports no response, caused by what Ktor raised, when ESPN ${outage.description}") {
            val thrown = shouldThrow<EspnUpstreamException> { outage.client().fetchScoreboard(DATE) }

            thrown.failure shouldBe EspnUpstreamFailure.NoResponse
            thrown.cause?.let { it::class } shouldBe outage.raises
        }

        test("fetchCalendar reports no response, caused by what Ktor raised, when ESPN ${outage.description}") {
            val thrown = shouldThrow<EspnUpstreamException> { outage.client().fetchCalendar() }

            thrown.failure shouldBe EspnUpstreamFailure.NoResponse
            thrown.cause?.let { it::class } shouldBe outage.raises
        }

        test("fetchScoreboard asks ESPN again after a request on which ESPN ${outage.description}") {
            val requests = AtomicInteger()
            val client = outage.client(requests, recovered = RECOVERED_SCOREBOARD)

            shouldThrow<EspnUpstreamException> { client.fetchScoreboard(DATE) }
            val recovered = client.fetchScoreboard(DATE)

            recovered.map { it.espnId } shouldBe listOf("espn-masters")
            requests.get() shouldBe 2
        }
    }

    // The one case on a real timer: it shows `HttpTimeout` gives up with that exception and not a cancellation.
    // The request never answers, so a slow machine can only make this later, never change its outcome.
    test("fetchScoreboard reports no response when Ktor's request timeout gives up on ESPN") {
        val client = espnClientOver(requestTimeout = 50.milliseconds) { awaitCancellation() }

        val thrown = shouldThrow<EspnUpstreamException> { client.fetchScoreboard(DATE) }

        thrown.failure shouldBe EspnUpstreamFailure.NoResponse
        thrown.cause?.let { it::class } shouldBe HttpRequestTimeoutException::class
    }

    test("fetchScoreboard lets a cancellation raised inside the request through as a cancellation") {
        val client = espnClientOver { throw CancellationException("request cancelled") }

        shouldThrow<CancellationException> { client.fetchScoreboard(DATE) }
    }

    test("fetchCalendar lets a cancellation raised inside the request through as a cancellation") {
        val client = espnClientOver { throw CancellationException("request cancelled") }

        shouldThrow<CancellationException> { client.fetchCalendar() }
    }
})
