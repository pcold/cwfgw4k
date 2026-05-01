package com.cwfgw.tournamentLinks

import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000bb1"))
private val FINALIZED_TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000bb2"))
private val GOLFER_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000cc1"))
private val SECOND_GOLFER_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000cc2"))
private val START_DATE: LocalDate = LocalDate.parse("2026-04-23")
private const val ESPN_EVENT_ID: String = "espn-zurich"

private fun tournament(
    id: TournamentId,
    status: TournamentStatus = TournamentStatus.Upcoming,
    pgaTournamentId: String? = ESPN_EVENT_ID,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = pgaTournamentId,
        name = "Zurich Classic",
        seasonId = com.cwfgw.seasons.SeasonId(UUID.randomUUID()),
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        isTeamEvent = true,
        week = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun golfer(
    id: GolferId = GOLFER_ID,
    firstName: String = "Matt",
    lastName: String = "Fitzpatrick",
): Golfer =
    Golfer(
        id = id,
        pgaPlayerId = null,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun espnCompetitor(
    espnId: String,
    name: String,
    position: Int,
    isTeamPartner: Boolean = false,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-10",
        scoreToPar = -10,
        totalStrokes = 270,
        roundScores = listOf(67, 68, 67, 68),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = isTeamPartner,
        pairKey = if (isTeamPartner) "team:t1" else null,
    )

private fun espnEvent(competitors: List<EspnCompetitor>): EspnTournament =
    EspnTournament(
        espnId = ESPN_EVENT_ID,
        name = "Zurich Classic",
        completed = false,
        competitors = competitors,
        isTeamEvent = true,
    )

private fun fixture(
    tournaments: List<Tournament> = listOf(tournament(TOURNAMENT_ID)),
    golfers: List<Golfer> = listOf(golfer()),
    initialOverrides: List<TournamentPlayerOverride> = emptyList(),
    scoreboard: Map<LocalDate, List<EspnTournament>> = emptyMap(),
): ApiFixture.() -> Unit =
    {
        val linkRepo = FakeTournamentLinkRepository(initial = initialOverrides)
        tournamentService = TournamentService(FakeTournamentRepository(initial = tournaments))
        golferService = GolferService(FakeGolferRepository(initial = golfers), FakeTransactor())
        teamService = TeamService(FakeTeamRepository(), FakeTransactor())
        seasonService = SeasonService(FakeSeasonRepository())
        tournamentLinkRepository = linkRepo
        tournamentLinkService = TournamentLinkService(linkRepo, tournamentService, golferService)
        espnService =
            EspnService(
                client = FakeEspnClient(tournamentsByDate = scoreboard),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
                seasonService = seasonService,
                tournamentLinkRepository = linkRepo,
            )
    }

class TournamentLinkRoutesSpec : FunSpec({

    test("POST /admin/tournaments/{id}/player-overrides returns 401 without an authenticated session") {
        apiTest(fixture()) { client ->
            val response =
                client.post("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/player-overrides") {
                    contentType(ContentType.Application.Json)
                    setBody(UpsertTournamentPlayerOverrideRequest("abc-123", GOLFER_ID))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /admin/tournaments/{id}/player-overrides upserts and returns the saved override") {
        authenticatedApiTest(fixture()) { client ->
            val response =
                client.post("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/player-overrides") {
                    contentType(ContentType.Application.Json)
                    setBody(UpsertTournamentPlayerOverrideRequest("abc-123", GOLFER_ID))
                }
            response.status shouldBe HttpStatusCode.OK
            val body: TournamentPlayerOverride = response.body()
            body.tournamentId shouldBe TOURNAMENT_ID
            body.espnCompetitorId shouldBe "abc-123"
            body.golferId shouldBe GOLFER_ID
        }
    }

    test("POST /admin/tournaments/{id}/player-overrides returns 404 when the tournament doesn't exist") {
        authenticatedApiTest(fixture(tournaments = emptyList())) { client ->
            val response =
                client.post("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/player-overrides") {
                    contentType(ContentType.Application.Json)
                    setBody(UpsertTournamentPlayerOverrideRequest("abc-123", GOLFER_ID))
                }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /admin/tournaments/{id}/player-overrides returns 409 when the tournament is finalized") {
        val configure =
            fixture(tournaments = listOf(tournament(FINALIZED_TOURNAMENT_ID, status = TournamentStatus.Completed)))
        authenticatedApiTest(configure) { client ->
            val response =
                client.post("/api/v1/admin/tournaments/${FINALIZED_TOURNAMENT_ID.value}/player-overrides") {
                    contentType(ContentType.Application.Json)
                    setBody(UpsertTournamentPlayerOverrideRequest("abc-123", GOLFER_ID))
                }
            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("POST /admin/tournaments/{id}/player-overrides returns 400 when the golfer doesn't exist") {
        val ghost = GolferId(UUID.randomUUID())
        authenticatedApiTest(fixture(golfers = emptyList())) { client ->
            val response =
                client.post("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/player-overrides") {
                    contentType(ContentType.Application.Json)
                    setBody(UpsertTournamentPlayerOverrideRequest("abc-123", ghost))
                }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("DELETE /admin/tournaments/{id}/player-overrides/{eid} returns 204 when the row was deleted") {
        val configure =
            fixture(initialOverrides = listOf(TournamentPlayerOverride(TOURNAMENT_ID, "abc-123", GOLFER_ID)))
        authenticatedApiTest(configure) { client ->
            val response = client.delete("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/player-overrides/abc-123")
            response.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("DELETE returns 404 when no override exists for that competitor") {
        authenticatedApiTest(fixture()) { client ->
            val response = client.delete("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/player-overrides/never")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("DELETE returns 409 when the tournament is finalized") {
        val configure =
            fixture(
                tournaments = listOf(tournament(FINALIZED_TOURNAMENT_ID, status = TournamentStatus.Completed)),
                initialOverrides =
                    listOf(TournamentPlayerOverride(FINALIZED_TOURNAMENT_ID, "abc-123", GOLFER_ID)),
            )
        authenticatedApiTest(configure) { client ->
            val response =
                client.delete("/api/v1/admin/tournaments/${FINALIZED_TOURNAMENT_ID.value}/player-overrides/abc-123")
            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("DELETE on a non-uuid tournament id returns 400") {
        authenticatedApiTest(fixture()) { client ->
            val response = client.delete("/api/v1/admin/tournaments/not-a-uuid/player-overrides/abc-123")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /admin/tournaments/{id}/competitors returns 401 without an authenticated session") {
        apiTest(fixture()) { client ->
            val response = client.get("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/competitors")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /admin/tournaments/{id}/competitors lists every competitor with override + match status") {
        // Two Fitzpatricks rostered. The partner row "team:t1:1" is pinned to Matt by an override;
        // the regular row matches Alex by full-name fallback.
        val matt = golfer(GOLFER_ID, "Matt", "Fitzpatrick")
        val alex = golfer(SECOND_GOLFER_ID, "Alex", "Fitzpatrick")
        val event =
            espnEvent(
                competitors =
                    listOf(
                        espnCompetitor("alex-1", "Alex Fitzpatrick", 1),
                        espnCompetitor("team:t1:1", "Fitzpatrick", 1, isTeamPartner = true),
                        espnCompetitor("nobody-1", "Nobody Special", 2),
                    ),
            )
        val configure =
            fixture(
                golfers = listOf(matt, alex),
                initialOverrides = listOf(TournamentPlayerOverride(TOURNAMENT_ID, "team:t1:1", matt.id)),
                scoreboard = mapOf(START_DATE to listOf(event)),
            )

        authenticatedApiTest(configure) { client ->
            val response = client.get("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/competitors")
            response.status shouldBe HttpStatusCode.OK
            val body: TournamentCompetitorListing = response.body()
            body.tournamentId shouldBe TOURNAMENT_ID
            body.isFinalized shouldBe false
            body.competitors shouldHaveSize 3

            val byEspnId = body.competitors.associateBy { it.espnCompetitorId }
            byEspnId.getValue("alex-1").linkedGolfer?.id shouldBe alex.id
            byEspnId.getValue("alex-1").hasOverride shouldBe false
            byEspnId.getValue("team:t1:1").linkedGolfer?.id shouldBe matt.id
            byEspnId.getValue("team:t1:1").hasOverride shouldBe true
            byEspnId.getValue("nobody-1").linkedGolfer shouldBe null
            byEspnId.getValue("nobody-1").hasOverride shouldBe false
        }
    }

    test("GET /admin/tournaments/{id}/competitors marks isFinalized when status is Completed") {
        val configure =
            fixture(
                tournaments = listOf(tournament(FINALIZED_TOURNAMENT_ID, status = TournamentStatus.Completed)),
                scoreboard = mapOf(START_DATE to listOf(espnEvent(competitors = emptyList()))),
            )
        authenticatedApiTest(configure) { client ->
            val response = client.get("/api/v1/admin/tournaments/${FINALIZED_TOURNAMENT_ID.value}/competitors")
            response.status shouldBe HttpStatusCode.OK
            response.body<TournamentCompetitorListing>().isFinalized shouldBe true
        }
    }

    test("GET /admin/tournaments/{id}/competitors returns 404 when the tournament doesn't exist") {
        authenticatedApiTest(fixture(tournaments = emptyList())) { client ->
            val response = client.get("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/competitors")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /admin/tournaments/{id}/competitors returns 409 when the tournament has no pgaTournamentId") {
        val configure =
            fixture(tournaments = listOf(tournament(TOURNAMENT_ID, pgaTournamentId = null)))
        authenticatedApiTest(configure) { client ->
            val response = client.get("/api/v1/admin/tournaments/${TOURNAMENT_ID.value}/competitors")
            response.status shouldBe HttpStatusCode.Conflict
        }
    }
})
