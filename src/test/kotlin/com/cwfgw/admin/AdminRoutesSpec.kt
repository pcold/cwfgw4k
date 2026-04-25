package com.cwfgw.admin

import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))

private fun golfer(
    idHex: String,
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000$idHex")),
        pgaPlayerId = "pga-$idHex",
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private const val ROSTER_HEADER = "team_number\tteam_name\tround\tplayer_name\townership_pct"

/**
 * Wire AdminService over fakes for a route test. Re-creates the dependent
 * services so the AdminService captured into [ApiFixture.adminService]
 * sees the seeded repos rather than the empty defaults.
 */
private fun adminFixture(
    seedSeason: Boolean = true,
    calendar: List<EspnCalendarEntry> = emptyList(),
    upstreamError: EspnUpstreamException? = null,
    seedGolfers: List<Golfer> = emptyList(),
): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        if (seedSeason) {
            kotlinx.coroutines.runBlocking {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                )
            }
        }
        val golferRepo = FakeGolferRepository(initial = seedGolfers)
        val tournamentRepo = FakeTournamentRepository()
        val teamRepo = FakeTeamRepository()
        seasonService = SeasonService(seasonRepo)
        tournamentService = TournamentService(tournamentRepo)
        golferService = GolferService(golferRepo)
        teamService = TeamService(teamRepo)
        espnService =
            EspnService(
                client = FakeEspnClient(calendar = calendar, upstreamError = upstreamError),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
            )
        adminService =
            AdminService(
                seasonService = seasonService,
                tournamentService = tournamentService,
                espnService = espnService,
                golferService = golferService,
                teamService = teamService,
            )
    }

private fun calendarEntry(
    id: String,
    label: String,
    isoDate: String,
): EspnCalendarEntry = EspnCalendarEntry(id = id, label = label, startDate = isoDate)

class AdminRoutesSpec : FunSpec({

    // ----- POST /api/v1/admin/seasons/{id}/upload -----

    test("POST /api/v1/admin/seasons/{id}/upload returns 401 without an authenticated session") {
        apiTest(adminFixture()) { client ->
            val response =
                client.post("/api/v1/admin/seasons/${SEASON_ID.value}/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(UploadSeasonRequest(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /api/v1/admin/seasons/{id}/upload returns 200 with the import result on success") {
        val configure =
            adminFixture(
                calendar =
                    listOf(
                        calendarEntry("e-1", "Sony Open", "2026-01-15T00:00Z"),
                        calendarEntry("e-2", "American Express", "2026-01-22T00:00Z"),
                    ),
            )
        authenticatedApiTest(configure) { client ->
            val response =
                client.post("/api/v1/admin/seasons/${SEASON_ID.value}/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(UploadSeasonRequest(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
                }
            response.status shouldBe HttpStatusCode.OK
            val body: SeasonImportResult = response.body()
            body.created shouldHaveSize 2
            body.created.map { it.name } shouldBe listOf("Sony Open", "American Express")
        }
    }

    test("POST /api/v1/admin/seasons/{id}/upload returns 404 when the season doesn't exist") {
        authenticatedApiTest(adminFixture(seedSeason = false)) { client ->
            val response =
                client.post("/api/v1/admin/seasons/${SEASON_ID.value}/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(UploadSeasonRequest(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
                }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/admin/seasons/{id}/upload returns 502 when ESPN is unavailable") {
        val configure =
            adminFixture(upstreamError = EspnUpstreamException(status = 503, message = "Service Unavailable"))
        authenticatedApiTest(configure) { client ->
            val response =
                client.post("/api/v1/admin/seasons/${SEASON_ID.value}/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(UploadSeasonRequest(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
                }
            response.status shouldBe HttpStatusCode.BadGateway
        }
    }

    test("POST /api/v1/admin/seasons/{non-uuid}/upload returns 400 for an unparseable id") {
        authenticatedApiTest(adminFixture()) { client ->
            val response =
                client.post("/api/v1/admin/seasons/not-a-uuid/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(UploadSeasonRequest(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
                }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // ----- POST /api/v1/admin/roster/preview -----

    test("POST /api/v1/admin/roster/preview returns 401 without an authenticated session") {
        apiTest(adminFixture()) { client ->
            val response =
                client.post("/api/v1/admin/roster/preview") {
                    contentType(ContentType.Text.Plain)
                    setBody(ROSTER_HEADER)
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /api/v1/admin/roster/preview returns 200 with the preview body") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        authenticatedApiTest(adminFixture(seedGolfers = listOf(scottie))) { client ->
            val tsv =
                """
                $ROSTER_HEADER
                1	BROWN	1	Scottie Scheffler	75
                """.trimIndent()
            val response =
                client.post("/api/v1/admin/roster/preview") {
                    contentType(ContentType.Text.Plain)
                    setBody(tsv)
                }
            response.status shouldBe HttpStatusCode.OK
            val body: RosterPreviewResult = response.body()
            body.matchedCount shouldBe 1
            body.teams.single().picks.single().match shouldBe
                PickMatch.Matched(golferId = scottie.id, golferName = "Scottie Scheffler")
        }
    }

    test("POST /api/v1/admin/roster/preview returns 400 with the parser error message when the TSV is bad") {
        authenticatedApiTest(adminFixture()) { client ->
            val response =
                client.post("/api/v1/admin/roster/preview") {
                    contentType(ContentType.Text.Plain)
                    setBody("nope\nrow")
                }
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsString().shouldContain("Header row must be exactly")
        }
    }

    // ----- POST /api/v1/admin/roster/confirm -----

    test("POST /api/v1/admin/roster/confirm returns 401 without an authenticated session") {
        apiTest(adminFixture()) { client ->
            val response =
                client.post("/api/v1/admin/roster/confirm") {
                    contentType(ContentType.Application.Json)
                    setBody(ConfirmRosterRequest(seasonId = SEASON_ID, teams = emptyList()))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /api/v1/admin/roster/confirm returns 200 with the upload result on success") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        authenticatedApiTest(adminFixture(seedGolfers = listOf(scottie))) { client ->
            val request =
                ConfirmRosterRequest(
                    seasonId = SEASON_ID,
                    teams =
                        listOf(
                            ConfirmedTeam(
                                teamNumber = 1,
                                teamName = "BROWN",
                                picks =
                                    listOf(
                                        ConfirmedPick(
                                            round = 1,
                                            ownershipPct = 75,
                                            assignment = GolferAssignment.Existing(scottie.id),
                                        ),
                                    ),
                            ),
                        ),
                )
            val response =
                client.post("/api/v1/admin/roster/confirm") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            response.status shouldBe HttpStatusCode.OK
            val body: RosterUploadResult = response.body()
            body.teamsCreated shouldBe 1
            body.golfersCreated shouldBe 0
            body.teams.single().teamName shouldBe "BROWN"
        }
    }

    test("POST /api/v1/admin/roster/confirm returns 400 listing every bad golfer id") {
        val ghost = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000901"))
        authenticatedApiTest(adminFixture()) { client ->
            val request =
                ConfirmRosterRequest(
                    seasonId = SEASON_ID,
                    teams =
                        listOf(
                            ConfirmedTeam(
                                teamNumber = 1,
                                teamName = "BROWN",
                                picks =
                                    listOf(
                                        ConfirmedPick(
                                            round = 1,
                                            ownershipPct = 75,
                                            assignment = GolferAssignment.Existing(ghost),
                                        ),
                                    ),
                            ),
                        ),
                )
            val response =
                client.post("/api/v1/admin/roster/confirm") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsString().shouldContain(ghost.value.toString())
        }
    }
})

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsString(): String = body()
