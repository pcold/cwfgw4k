package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))

private fun tournament(
    id: TournamentId,
    name: String,
    startDate: String,
    status: TournamentStatus = TournamentStatus.Upcoming,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = SEASON_ID,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun opsFixture(initialTournaments: List<Tournament> = emptyList()): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        runBlocking {
            with(noopTransactionContext) {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                )
            }
        }
        seasonRepository = seasonRepo
        tournamentRepository = FakeTournamentRepository(initial = initialTournaments)
    }

class SeasonOpsRoutesSpec : FunSpec({

    test("POST /api/v1/seasons/{sid}/clean-results returns 401 without an authenticated session") {
        apiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/seasons/${SEASON_ID.value}/clean-results")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /api/v1/seasons/{sid}/clean-results returns 200 with deletion counts") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        authenticatedApiTest(opsFixture(initialTournaments = listOf(masters))) { client ->
            val response = client.post("/api/v1/seasons/${SEASON_ID.value}/clean-results")
            response.status shouldBe HttpStatusCode.OK
            val body: CleanSeasonResult = response.body()
            body.tournamentsReset shouldBe 1
        }
    }

    test("POST /api/v1/seasons/{unknown-uuid}/clean-results returns 404 naming the season") {
        val unknown = UUID.fromString("00000000-0000-0000-0000-000000000fff")
        authenticatedApiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/seasons/$unknown/clean-results")
            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText().shouldContain("season $unknown not found")
        }
    }

    test("POST /api/v1/seasons/{non-uuid}/clean-results returns 400") {
        authenticatedApiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/seasons/not-a-uuid/clean-results")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
