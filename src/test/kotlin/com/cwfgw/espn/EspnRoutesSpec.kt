package com.cwfgw.espn

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val START_DATE = LocalDate.parse("2026-04-15")

private fun espnCompetitor(
    espnId: String,
    name: String,
    position: Int,
    scoreToPar: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = scoreToPar.toString(),
        scoreToPar = scoreToPar,
        totalStrokes = 280,
        roundScores = listOf(70, 70, 70, 70),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun espnTournament(
    espnId: String = "401580999",
    competitors: List<EspnCompetitor> = emptyList(),
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = "Test Open",
        completed = true,
        competitors = competitors,
        isTeamEvent = false,
    )

private fun withWiredService(
    tournaments: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    calendar: List<EspnCalendarEntry> = emptyList(),
    upstreamError: EspnUpstreamException? = null,
    seedTournaments: List<CreateTournamentRequest> = emptyList(),
): ApiFixture.() -> Unit =
    {
        val tournamentRepo = FakeTournamentRepository()
        val golferRepo = FakeGolferRepository()
        val teamRepo = FakeTeamRepository()
        val tournamentSvc = TournamentService(tournamentRepo)
        // Seed via the repo's suspending API. The configure block runs on the test setup thread before any
        // HTTP request is made, so blocking here is fine and avoids forcing every spec to be suspend-aware.
        runBlocking {
            seedTournaments.forEach { request -> tournamentRepo.create(request) }
        }
        tournamentService = tournamentSvc
        golferService = GolferService(golferRepo)
        teamService = TeamService(teamRepo)
        espnService =
            EspnService(
                client =
                    FakeEspnClient(
                        tournamentsByDate = tournaments,
                        calendar = calendar,
                        upstreamError = upstreamError,
                    ),
                tournamentService = tournamentSvc,
                golferService = golferService,
                teamService = teamService,
                seasonService = seasonService,
                tournamentLinkRepository = FakeTournamentLinkRepository(),
            )
    }

class EspnRoutesSpec : FunSpec({

    test("POST /espn/import?date=YYYY-MM-DD returns 200 with the import batch on success") {
        authenticatedApiTest(withWiredService(tournaments = mapOf(START_DATE to emptyList()))) { client ->
            val response = client.post("/api/v1/espn/import?date=2026-04-15")

            response.status shouldBe HttpStatusCode.OK
            val batch = response.body<EspnImportBatch>()
            batch.imported shouldBe emptyList()
            batch.unlinked shouldBe emptyList()
        }
    }

    test("POST /espn/import returns 400 when the date query parameter is missing") {
        authenticatedApiTest(withWiredService()) { client ->
            client.post("/api/v1/espn/import").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /espn/import?date=garbage returns 400 for a malformed date") {
        authenticatedApiTest(withWiredService()) { client ->
            client.post("/api/v1/espn/import?date=not-a-date").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /espn/import returns 502 when ESPN responds with an upstream error") {
        authenticatedApiTest(
            withWiredService(upstreamError = EspnUpstreamException(503, "Service Unavailable")),
        ) { client ->
            client.post("/api/v1/espn/import?date=2026-04-15").status shouldBe HttpStatusCode.BadGateway
        }
    }

    test("POST /espn/import surfaces unlinked events to the response without failing the batch") {
        val unlinked = espnTournament(espnId = "no-match", competitors = emptyList())
        authenticatedApiTest(withWiredService(tournaments = mapOf(START_DATE to listOf(unlinked)))) { client ->
            val response = client.post("/api/v1/espn/import?date=2026-04-15")

            response.status shouldBe HttpStatusCode.OK
            val batch = response.body<EspnImportBatch>()
            batch.imported shouldBe emptyList()
            batch.unlinked.single().espnEventId shouldBe "no-match"
        }
    }

    test("POST /espn/import/tournament/{id} returns 200 with the import on success") {
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Rory McIlroy", 1, -10)))
        val seed =
            CreateTournamentRequest(
                name = "Test Open",
                seasonId = SEASON_ID,
                startDate = START_DATE,
                endDate = START_DATE.plusDays(3),
                pgaTournamentId = "401580999",
            )
        authenticatedApiTest(
            withWiredService(
                tournaments = mapOf(START_DATE to listOf(event)),
                seedTournaments = listOf(seed),
            ),
        ) { client ->
            // The seeded tournament's id is generated by the fake; look it up first via the espn batch route.
            val batchResponse = client.post("/api/v1/espn/import?date=2026-04-15")
            batchResponse.status shouldBe HttpStatusCode.OK
            val tournamentId = batchResponse.body<EspnImportBatch>().imported.single().tournamentId

            val response = client.post("/api/v1/espn/import/tournament/${tournamentId.value}")
            response.status shouldBe HttpStatusCode.OK
            response.body<EspnImport>().matched shouldBe 1
        }
    }

    test("POST /espn/import/tournament/{bad} returns 400 for a malformed tournament id") {
        authenticatedApiTest(withWiredService()) { client ->
            client.post("/api/v1/espn/import/tournament/not-a-uuid")
                .status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /espn/import/tournament/{unknown} returns 404 when the tournament does not exist") {
        val unknown = TournamentId(UUID.randomUUID())
        authenticatedApiTest(withWiredService()) { client ->
            client.post("/api/v1/espn/import/tournament/${unknown.value}")
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /espn/import/tournament/{unlinked} returns 409 when the tournament has no pga_tournament_id") {
        val seed =
            CreateTournamentRequest(
                name = "Unlinked",
                seasonId = SEASON_ID,
                startDate = START_DATE,
                endDate = START_DATE.plusDays(3),
                // No pgaTournamentId
            )
        authenticatedApiTest(withWiredService(seedTournaments = listOf(seed))) { client ->
            // Look up the seeded tournament's id by calling the unlinked-event branch first, since seeding doesn't
            // expose the id directly. Easier: hit a tournament-list endpoint — but we don't have one in scope here.
            // Instead, drive through the route with a known-absent seed and let the batch result tell us.
            // Because the tournament has no pgaTournamentId, no ESPN event will match — so we need to hit
            // /tournament/{id} directly with the actual seeded id. Use the tournaments list endpoint.
            val tournamentsList =
                client.get("/api/v1/tournaments").body<List<com.cwfgw.tournaments.Tournament>>()
            val seededId = tournamentsList.single().id

            client.post("/api/v1/espn/import/tournament/${seededId.value}")
                .status shouldBe HttpStatusCode.Conflict
        }
    }

    test("POST /espn/import returns 401 without an authenticated session") {
        apiTest { client ->
            client.post("/api/v1/espn/import?date=2026-04-15").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /espn/import/tournament/{id} returns 401 without an authenticated session") {
        apiTest { client ->
            client.post("/api/v1/espn/import/tournament/${UUID.randomUUID()}")
                .status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /espn/calendar returns 200 with the upstream calendar entries — public, no auth required") {
        val entries =
            listOf(
                EspnCalendarEntry(id = "401580001", label = "The Sentry", startDate = "2026-01-02T00:00Z"),
                EspnCalendarEntry(id = "401580999", label = "Masters", startDate = "2026-04-09T00:00Z"),
            )
        apiTest(withWiredService(calendar = entries)) { client ->
            val response = client.get("/api/v1/espn/calendar")

            response.status shouldBe HttpStatusCode.OK
            response.body<List<EspnCalendarEntry>>() shouldBe entries
        }
    }

    test("GET /espn/calendar returns 502 when ESPN responds with an upstream error") {
        apiTest(
            withWiredService(upstreamError = EspnUpstreamException(503, "Service Unavailable")),
        ) { client ->
            client.get("/api/v1/espn/calendar").status shouldBe HttpStatusCode.BadGateway
        }
    }

    test("GET /espn/preview/{sid}?date= returns 200 with the live preview list") {
        apiTest(withWiredService(tournaments = mapOf(START_DATE to emptyList()))) { client ->
            val response = client.get("/api/v1/espn/preview/${SEASON_ID.value}?date=2026-04-15")
            response.status shouldBe HttpStatusCode.OK
            response.body<List<EspnLivePreview>>() shouldBe emptyList()
        }
    }

    test("GET /espn/preview/{sid} returns 400 when the date query parameter is missing") {
        apiTest(withWiredService()) { client ->
            client.get("/api/v1/espn/preview/${SEASON_ID.value}").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /espn/preview/{sid}?date=garbage returns 400 for a malformed date") {
        apiTest(withWiredService()) { client ->
            val response = client.get("/api/v1/espn/preview/${SEASON_ID.value}?date=not-a-date")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /espn/preview/{non-uuid}?date=... returns 400 for a malformed season id") {
        apiTest(withWiredService()) { client ->
            client.get("/api/v1/espn/preview/not-a-uuid?date=2026-04-15").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /espn/preview/{sid}?date= returns 502 when ESPN is down") {
        apiTest(
            withWiredService(upstreamError = EspnUpstreamException(503, "Service Unavailable")),
        ) { client ->
            val response = client.get("/api/v1/espn/preview/${SEASON_ID.value}?date=2026-04-15")
            response.status shouldBe HttpStatusCode.BadGateway
        }
    }
})
