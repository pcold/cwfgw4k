package com.cwfgw.tournaments

import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.http.appJson
import com.cwfgw.leagues.LeagueId
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterViewPick
import com.cwfgw.teams.RosterViewTeam
import com.cwfgw.teams.TeamId
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.shouldBeRefused
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val CREATED_GOLFER_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val GOLFER_CLOCK: Instant = Instant.parse("2026-04-12T23:00:00Z")

private fun golferRow(
    id: String,
    pgaPlayerId: String?,
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.fromString(id)),
        pgaPlayerId = pgaPlayerId,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        updatedAt = GOLFER_CLOCK,
    )

private val NICOLAI_HOJGAARD =
    golferRow(
        id = "00000000-0000-0000-0000-000000000c11",
        pgaPlayerId = "4410",
        firstName = "Nicolai",
        lastName = "Højgaard",
    )

// Ranked so the fake lists them, and so the matcher names them, in this order.
private val TOM_KIM =
    golferRow(
        id = "00000000-0000-0000-0000-000000000c12",
        pgaPlayerId = null,
        firstName = "Tom",
        lastName = "Kim",
    ).copy(worldRanking = 20)
private val TOM_KIM_TWIN =
    golferRow(
        id = "00000000-0000-0000-0000-000000000c13",
        pgaPlayerId = null,
        firstName = "Tom",
        lastName = "Kim",
    ).copy(worldRanking = 21)

private fun rosterOf(golfers: List<Golfer>): List<RosterViewTeam> =
    listOf(
        RosterViewTeam(
            teamId = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000d01")),
            teamNumber = 1,
            teamName = "Alpha",
            picks =
                golfers.mapIndexed { index, golfer ->
                    RosterViewPick(
                        round = index + 1,
                        golferName = "${golfer.firstName} ${golfer.lastName}",
                        ownershipPct = BigDecimal(100),
                        golferId = golfer.id,
                    )
                },
        ),
    )

private fun competitor(
    espnId: String,
    name: String,
    order: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = order,
        scoreStr = "-$order",
        scoreToPar = -order,
        totalStrokes = 280,
        roundScores = listOf(70, 70, 70, 70),
        position = order,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private suspend fun HttpResponse.wireJson(): JsonElement = appJson.parseToJsonElement(bodyAsText())

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

private fun opsFixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    espnByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    startDateMovedDuringFetch: LocalDate? = null,
    golfers: List<Golfer> = emptyList(),
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
        seasonRepository = seasonRepo
        tournamentRepository = tournamentRepo
        teamRepository = FakeTeamRepository(initialRosterView = mapOf(SEASON_ID to rosterOf(golfers)))
        golferRepository = FakeGolferRepository(golfers, idFactory = { CREATED_GOLFER_ID }, clock = { GOLFER_CLOCK })
        val scoreboard = FakeEspnClient(tournamentsByDate = espnByDate)
        espnClient =
            startDateMovedDuringFetch?.let { StartDateMovingEspnClient(scoreboard, tournamentRepo, it) } ?: scoreboard
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
            val finalized = response.body<FinalizedTournament>()
            finalized.tournament.status shouldBe TournamentStatus.Completed
            response.wireJson().jsonObject.getValue("import_warnings") shouldBe
                appJson.parseToJsonElement(
                    """
                    [{
                      "tournament_id": "${MASTERS_ID.value}",
                      "tournament_name": "The Masters",
                      "rostered_surname_twins": [],
                      "created_golfers": [],
                      "unmatched_competitors": [],
                      "contested_competitors": []
                    }]
                    """,
                )
        }
    }

    test("POST /api/v1/tournaments/{id}/finalize names each import warning beside the tournament, in snake case") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09")
        val field =
            listOf(
                competitor(espnId = "9999", name = "Rasmus Hojgaard", order = 1),
                competitor(espnId = "5501", name = "Tom Kim", order = 2),
                competitor(espnId = "5502", name = "", order = 3),
            )
        val event = emptyEspnEvent("espn-${MASTERS_ID.value}", "Masters").copy(competitors = field)
        val configure =
            opsFixture(
                initialTournaments = listOf(masters),
                espnByDate = mapOf(LocalDate.parse("2026-04-09") to listOf(event)),
                golfers = listOf(NICOLAI_HOJGAARD, TOM_KIM, TOM_KIM_TWIN),
            )
        val created =
            golferRow(
                id = CREATED_GOLFER_ID.value.toString(),
                pgaPlayerId = "9999",
                firstName = "Rasmus",
                lastName = "Hojgaard",
            )

        authenticatedApiTest(configure) { client ->
            val response = client.post("/api/v1/tournaments/${MASTERS_ID.value}/finalize")
            response.status shouldBe HttpStatusCode.OK
            val wire = response.wireJson().jsonObject
            wire.keys shouldBe setOf("tournament", "import_warnings")
            wire.getValue("import_warnings") shouldBe
                appJson.parseToJsonElement(
                    """
                    [{
                      "tournament_id": "${MASTERS_ID.value}",
                      "tournament_name": "The Masters",
                      "rostered_surname_twins": [
                        {"created": ${created.wire()}, "rostered": [${NICOLAI_HOJGAARD.wire()}]}
                      ],
                      "created_golfers": [],
                      "unmatched_competitors": [{"espn_competitor_id": "5502", "name": ""}],
                      "contested_competitors": [
                        {
                          "espn_competitor_id": "5501",
                          "name": "Tom Kim",
                          "candidates": [${TOM_KIM.wire()}, ${TOM_KIM_TWIN.wire()}]
                        }
                      ]
                    }]
                    """,
                )
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

    test("POST /api/v1/tournaments/{id}/finalize is refused when the tournament is already completed") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
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
            response.shouldBeRefused("tournament_finalized")
            response.bodyAsText().shouldContain("tournament ${MASTERS_ID.value} is already finalized")
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

private fun Golfer.wire(): String = appJson.encodeToString(serializer<Golfer>(), this)
