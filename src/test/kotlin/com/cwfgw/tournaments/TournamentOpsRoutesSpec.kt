package com.cwfgw.tournaments

import com.cwfgw.espn.EspnClient
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
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.shouldBeRefused
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
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

/** Moves [MASTERS_ID] to [movedTo] while finalize's fetch is in flight. */
private class StartDateMovingEspnClient(
    private val delegate: EspnClient,
    private val tournaments: TournamentRepository,
    private val movedTo: LocalDate,
) : EspnClient by delegate {
    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        with(noopTransactionContext) { tournaments.update(MASTERS_ID, UpdateTournamentRequest(startDate = movedTo)) }
        return delegate.fetchScoreboard(date)
    }
}

@Suppress("LongParameterList")
private fun opsFixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    espnByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    startDateMovedDuringFetch: LocalDate? = null,
): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        if (seedSeason) {
            runBlocking {
                with(noopTransactionContext) {
                    seasonRepo.create(
                        CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                    )
                }
            }
        }
        val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
        val teamRepo = FakeTeamRepository()
        val golferRepo = FakeGolferRepository()
        val scoringRepo = FakeScoringRepository()
        val sharedTx = transactor
        seasonService = SeasonService(seasonRepo, sharedTx)
        tournamentService = TournamentService(tournamentRepo, sharedTx)
        teamService = TeamService(teamRepo, sharedTx)
        golferService = GolferService(golferRepo, sharedTx)
        val linkRepo = FakeTournamentLinkRepository()
        val scoreboard = FakeEspnClient(tournamentsByDate = espnByDate)
        val client =
            startDateMovedDuringFetch?.let { StartDateMovingEspnClient(scoreboard, tournamentRepo, it) } ?: scoreboard
        espnService =
            testEspnService(
                client = client,
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
                linkRepository = linkRepo,
                tx = sharedTx,
            )
        scoringService = ScoringService(scoringRepo, seasonRepo, tournamentRepo, teamRepo)
        tournamentOpsService =
            TournamentOpsService(
                tournamentRepository = tournamentRepo,
                scoringService = scoringService,
                scoringRepository = scoringRepo,
                espnService = espnService,
                tx = sharedTx,
            )
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

    test("POST /api/v1/tournaments/{id}/finalize returns 404 when scoring can't find the tournament's season") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09")
        val configure =
            opsFixture(
                seedSeason = false,
                initialTournaments = listOf(masters),
                espnByDate =
                    mapOf(
                        LocalDate.parse("2026-04-09") to
                            listOf(emptyEspnEvent("espn-${MASTERS_ID.value}", "Masters")),
                    ),
            )

        authenticatedApiTest(configure) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText().shouldContain("season ${SEASON_ID.value} not found")
        }
    }

    test("POST /api/v1/tournaments/{id}/finalize is refused when an earlier tournament is unfinalized") {
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
            response.shouldBeRefused("tournament_out_of_order")
            response.bodyAsText().shouldContain("Sony Open")
        }
    }

    test("POST /api/v1/tournaments/{id}/finalize is refused when the start date moved during the ESPN fetch") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09")
        val configure =
            opsFixture(
                initialTournaments = listOf(masters),
                startDateMovedDuringFetch = LocalDate.parse("2026-04-16"),
            )

        authenticatedApiTest(configure) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.shouldBeRefused("tournament_start_date_changed")
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

    test("POST /api/v1/tournaments/{id}/reset is refused when a later tournament is already completed") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Completed)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        authenticatedApiTest(opsFixture(initialTournaments = listOf(sony, masters))) { client ->
            val response = client.post("/api/v1/tournaments/${SONY_ID.value}/reset")
            response.shouldBeRefused("tournament_out_of_order")
            response.bodyAsText().shouldContain("The Masters")
        }
    }
})
