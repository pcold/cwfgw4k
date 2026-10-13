package com.cwfgw.espn

import com.cwfgw.result.Result
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.FakeTournamentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

private val NO_RESPONSE = Result.Err(EspnError.UpstreamUnavailable(EspnUpstreamFailure.NoResponse))

private object CancelledEspnClient : EspnClient {
    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> =
        throw CancellationException("caller went away")

    override suspend fun fetchCalendar(): List<EspnCalendarEntry> = throw CancellationException("caller went away")
}

class EspnServiceOutageSpec : FunSpec({

    EspnOutage.entries.forEach { outage ->
        test("importByDate returns UpstreamUnavailable with no response when ESPN ${outage.description}") {
            val service = testEspnService(client = outage.client())

            service.importByDate(OUTAGE_TOURNAMENT.startDate) shouldBe NO_RESPONSE
        }

        test("listCompetitorsForLinking returns UpstreamUnavailable with no response when ESPN ${outage.description}") {
            val linked = FakeTournamentRepository(initial = listOf(OUTAGE_TOURNAMENT))
            val service = testEspnService(client = outage.client(), tournamentRepository = linked)

            service.listCompetitorsForLinking(OUTAGE_TOURNAMENT.id) shouldBe NO_RESPONSE
        }
    }

    test("importByDate lets a CancellationException from the client propagate") {
        val service = testEspnService(client = CancelledEspnClient)

        shouldThrow<CancellationException> { service.importByDate(OUTAGE_TOURNAMENT.startDate) }
    }
})
