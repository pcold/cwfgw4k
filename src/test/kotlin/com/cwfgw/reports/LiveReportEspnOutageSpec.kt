package com.cwfgw.reports

import com.cwfgw.espn.EspnClient
import com.cwfgw.leagues.LeagueId
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.EspnOutage
import com.cwfgw.testing.OUTAGE_TOURNAMENT
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.FakeTournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = OUTAGE_TOURNAMENT.seasonId
private val MASTERS_ID = OUTAGE_TOURNAMENT.id

private class LiveCapablePath(
    val label: String,
    val path: String = label,
    val fallback: (base: String) -> String = { it },
)

private val LIVE_CAPABLE_PATHS =
    listOf(
        LiveCapablePath("report"),
        LiveCapablePath("report/{tid}", path = "report/${MASTERS_ID.value}"),
        LiveCapablePath("rankings"),
        // Echoes the request's `live` flag whether or not ESPN answered, so its fallback differs in that field.
        LiveCapablePath("player-rankings", fallback = { it.replace("\"live\":false", "\"live\":true") }),
    )

private const val MASTERS_SCOREBOARD =
    """{"events":[{"id":"espn-masters","name":"The Masters","competitions":[{"competitors":[]}]}]}"""

private fun mastersInPlay(espn: EspnClient): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        with(noopTransactionContext) {
            seasonRepo.create(CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026))
        }
        val tournamentRepo = FakeTournamentRepository(initial = listOf(OUTAGE_TOURNAMENT))
        liveOverlayService =
            LiveOverlayService(
                testEspnService(
                    client = espn,
                    seasonRepository = seasonRepo,
                    golferRepository = golferRepository,
                    teamRepository = teamRepository,
                    tournamentRepository = tournamentRepo,
                ),
            )
        weeklyReportService =
            WeeklyReportService(
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepository,
                golferRepository = golferRepository,
                scoringRepository = scoringRepository,
                liveOverlayService = liveOverlayService,
                tx = transactor,
            )
    }

class LiveReportEspnOutageSpec : FunSpec({

    EspnOutage.entries.forEach { outage ->
        LIVE_CAPABLE_PATHS.forEach { report ->
            val name = "GET /api/v1/seasons/{id}/${report.label}?live=true"
            test("$name returns the base report when ESPN ${outage.description}") {
                val requests = AtomicInteger()
                apiTest(mastersInPlay(outage.client(requests))) { client ->
                    val url = "/api/v1/seasons/${SEASON_ID.value}/${report.path}"

                    val live = client.get("$url?live=true")

                    live.status shouldBe HttpStatusCode.OK
                    live.bodyAsText() shouldBe report.fallback(client.get(url).bodyAsText())
                    requests.get() shouldBeGreaterThan 0
                }
            }
        }

        test("GET /api/v1/seasons/{id}/report/{tid}?live=true asks ESPN again after ESPN ${outage.description}") {
            val espn = outage.client(recovered = MASTERS_SCOREBOARD)
            apiTest(mastersInPlay(espn)) { client ->
                val url = "/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}?live=true"

                client.get(url).body<WeeklyReport>().live shouldBe null
                client.get(url).body<WeeklyReport>().live shouldBe true
            }
        }
    }
})
