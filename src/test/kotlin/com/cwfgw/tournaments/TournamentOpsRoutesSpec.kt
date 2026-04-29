package com.cwfgw.tournaments

import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))

private fun tournament(
    id: TournamentId,
    name: String,
    startDate: String,
    status: TournamentStatus = TournamentStatus.Upcoming,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = "espn-${id.value}",
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

private fun emptyEspnEvent(
    espnId: String,
    name: String,
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = name,
        completed = true,
        competitors = emptyList(),
        isTeamEvent = false,
    )

@Suppress("LongParameterList")
private fun opsFixture(
    initialTournaments: List<Tournament> = emptyList(),
    espnByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        kotlinx.coroutines.runBlocking {
            seasonRepo.create(
                CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
            )
        }
        val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
        val teamRepo = FakeTeamRepository()
        val golferRepo = FakeGolferRepository()
        val scoringRepo = FakeScoringRepository()
        seasonService = SeasonService(seasonRepo)
        tournamentService = TournamentService(tournamentRepo)
        teamService = TeamService(teamRepo)
        golferService = GolferService(golferRepo)
        espnService =
            EspnService(
                client = FakeEspnClient(tournamentsByDate = espnByDate),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
                seasonService = seasonService,
                tournamentLinkRepository = FakeTournamentLinkRepository(),
            )
        scoringService = ScoringService(scoringRepo, seasonService, tournamentService, teamService)
        tournamentOpsService = TournamentOpsService(tournamentService, scoringService, espnService)
    }

class TournamentOpsRoutesSpec : FunSpec({

    test("POST /api/v1/tournaments/{id}/finalize returns 401 without an authenticated session") {
        apiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /api/v1/tournaments/{id}/finalize returns 200 with the finalized tournament on success") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09")
        val configure =
            opsFixture(
                initialTournaments = listOf(masters),
                espnByDate =
                    mapOf(
                        LocalDate.parse("2026-04-09") to
                            listOf(emptyEspnEvent("espn-${MASTERS_ID.value}", "Masters")),
                    ),
            )

        authenticatedApiTest(configure) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.status shouldBe HttpStatusCode.OK
            response.body<Tournament>().status shouldBe TournamentStatus.Completed
        }
    }

    test("POST /api/v1/tournaments/{id}/finalize returns 404 when the tournament doesn't exist") {
        authenticatedApiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/tournaments/{id}/finalize returns 409 when an earlier tournament is unfinalized") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Upcoming)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Upcoming)
        val configure =
            opsFixture(
                initialTournaments = listOf(sony, masters),
                espnByDate =
                    mapOf(
                        LocalDate.parse("2026-04-09") to
                            listOf(emptyEspnEvent("espn-${MASTERS_ID.value}", "Masters")),
                    ),
            )

        authenticatedApiTest(configure) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.status shouldBe HttpStatusCode.Conflict
            response.bodyAsText().shouldContain("Sony Open")
        }
    }

    test("POST /api/v1/tournaments/{non-uuid}/finalize returns 400") {
        authenticatedApiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/tournaments/not-a-uuid/finalize")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/tournaments/{id}/reset returns 401 without an authenticated session") {
        apiTest(opsFixture()) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/reset")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /api/v1/tournaments/{id}/reset returns 200 and reverts status to upcoming") {
        val masters =
            tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        authenticatedApiTest(opsFixture(initialTournaments = listOf(masters))) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/reset")
            response.status shouldBe HttpStatusCode.OK
            response.body<Tournament>().status shouldBe TournamentStatus.Upcoming
        }
    }

    test("POST /api/v1/tournaments/{id}/reset returns 409 when a later tournament is already completed") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Completed)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        authenticatedApiTest(opsFixture(initialTournaments = listOf(sony, masters))) { client ->
            val response = client.post("/api/v1/tournaments/${SONY_ID.value}/reset")
            response.status shouldBe HttpStatusCode.Conflict
            response.bodyAsText().shouldContain("The Masters")
        }
    }
})
