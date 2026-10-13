package com.cwfgw.espn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

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

    test("fetchScoreboard lets a cancellation raised inside the request through as a cancellation") {
        val client = espnClientOver { throw CancellationException("request cancelled") }

        shouldThrow<CancellationException> { client.fetchScoreboard(DATE) }
    }

    test("fetchCalendar lets a cancellation raised inside the request through as a cancellation") {
        val client = espnClientOver { throw CancellationException("request cancelled") }

        shouldThrow<CancellationException> { client.fetchCalendar() }
    }
})
