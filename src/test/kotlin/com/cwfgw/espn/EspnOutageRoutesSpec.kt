package com.cwfgw.espn

import ch.qos.logback.classic.Level
import com.cwfgw.http.ErrorBody
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.CapturedLogs
import com.cwfgw.testing.EspnOutage
import com.cwfgw.testing.OUTAGE_TOURNAMENT
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.captureLogs
import com.cwfgw.testing.espnClientOver
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.FakeTournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

private val IMPORT_URL = "/api/v1/espn/import?date=${OUTAGE_TOURNAMENT.startDate}"
private val COMPETITORS_URL = "/api/v1/admin/tournaments/${OUTAGE_TOURNAMENT.id.value}/competitors"
private const val CALENDAR_URL = "/api/v1/espn/calendar"

/** [logged] is what the WARN line has to carry for a reader to tell this failure from the others. */
private data class FailingUpstream(
    val description: String,
    val error: String,
    val logged: String,
    val client: () -> EspnClient,
)

// The 503 sits beside the outages so one set of assertions holds both to the same status and log level.
private val UPSTREAM_FAILURES: List<FailingUpstream> =
    EspnOutage.entries.map { outage ->
        FailingUpstream(
            description = "ESPN ${outage.description}",
            error = "ESPN did not respond",
            logged = outage.raises.java.name,
            client = { outage.client() },
        )
    } +
        FailingUpstream(
            description = "ESPN answers 503",
            error = "ESPN returned 503",
            logged = "ESPN returned 503",
            client = { espnClientOver { respondError(HttpStatusCode.ServiceUnavailable) } },
        )

private fun espnFailing(failure: FailingUpstream): ApiFixture.() -> Unit =
    {
        espnService =
            testEspnService(
                client = failure.client(),
                tournamentRepository = FakeTournamentRepository(initial = listOf(OUTAGE_TOURNAMENT)),
                tx = transactor,
            )
    }

private suspend fun HttpResponse.shouldBeBadGateway(error: String) {
    status shouldBe HttpStatusCode.BadGateway
    body<ErrorBody>().error shouldBe error
}

private fun CapturedLogs.scoreboardFailuresAtWarn(): List<String> =
    events()
        .filter { it.level == Level.WARN }
        .map { it.formattedMessage }
        .filter { it.startsWith("ESPN scoreboard fetch failed") }

class EspnOutageRoutesSpec : FunSpec({

    UPSTREAM_FAILURES.forEach { failure ->
        test("POST /espn/import returns 502 when ${failure.description}") {
            authenticatedApiTest(espnFailing(failure)) { client ->
                client.post(IMPORT_URL).shouldBeBadGateway(failure.error)
            }
        }

        test("GET /admin/tournaments/{id}/competitors returns 502 when ${failure.description}") {
            authenticatedApiTest(espnFailing(failure)) { client ->
                client.get(COMPETITORS_URL).shouldBeBadGateway(failure.error)
            }
        }

        test("GET /espn/calendar returns 502 when ${failure.description}") {
            apiTest(espnFailing(failure)) { client ->
                client.get(CALENDAR_URL).shouldBeBadGateway(failure.error)
            }
        }

        test("POST /espn/import logs one WARN naming the failure when ${failure.description}") {
            captureLogs { logs ->
                authenticatedApiTest(espnFailing(failure)) { client -> client.post(IMPORT_URL) }

                logs.scoreboardFailuresAtWarn().single() shouldContain failure.logged
            }
        }
    }
})
