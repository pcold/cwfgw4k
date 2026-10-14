package com.cwfgw.reports

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.espn.CachingEspnClient
import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.http.appJson
import com.cwfgw.leagues.LeagueId
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.FantasyScoreId
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.ESPN_SCHEDULE_ZONE
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val TEAM_A = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val TEAM_B = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c02"))
private val SCOTTIE_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))
private val RORY_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d02"))
private val XANDER_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d03"))

private val LIVE_CAPABLE_PATHS =
    listOf(
        "report" to "report",
        "report/{tid}" to "report/${MASTERS_ID.value}",
        "rankings" to "rankings",
        "player-rankings" to "player-rankings",
    )

private fun teamRow(
    id: TeamId,
    name: String,
): Team =
    Team(
        id = id,
        seasonId = SEASON_ID,
        ownerName = "Owner $name",
        teamName = name,
        teamNumber = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun golferRow(
    id: GolferId,
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = id,
        pgaPlayerId = "pga-${id.value}",
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun tournamentRow(
    id: TournamentId,
    name: String,
    startDate: String,
    week: String? = null,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = SEASON_ID,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
        courseName = null,
        status = TournamentStatus.Completed,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = week,
        isTeamEvent = false,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun rosterRow(
    teamId: TeamId,
    golferId: GolferId,
    draftRound: Int,
): RosterEntry =
    RosterEntry(
        id = RosterEntryId(UUID.randomUUID()),
        teamId = teamId,
        golferId = golferId,
        acquiredVia = "draft",
        draftRound = draftRound,
        ownershipPct = BigDecimal(100),
        acquiredAt = Instant.parse("2026-01-01T00:00:00Z"),
        droppedAt = null,
        isActive = true,
    )

private fun resultRow(
    tournamentId: TournamentId,
    golferId: GolferId,
    position: Int?,
): TournamentResult =
    TournamentResult(
        id = TournamentResultId(UUID.randomUUID()),
        tournamentId = tournamentId,
        golferId = golferId,
        position = position,
        scoreToPar = -5,
        totalStrokes = 270,
        earnings = null,
        round1 = null,
        round2 = null,
        round3 = null,
        round4 = null,
        madeCut = position != null,
        pairKey = null,
    )

private fun scoreRow(
    teamId: TeamId,
    tournamentId: TournamentId,
    golferId: GolferId,
    points: BigDecimal,
): FantasyScore =
    FantasyScore(
        id = FantasyScoreId(UUID.randomUUID()),
        seasonId = SEASON_ID,
        teamId = teamId,
        tournamentId = tournamentId,
        golferId = golferId,
        points = points,
        position = 1,
        numTied = 1,
        basePayout = points,
        ownershipPct = BigDecimal(100),
        payout = points,
        multiplier = BigDecimal.ONE,
        calculatedAt = Instant.parse("2026-04-12T20:00:00Z"),
    )

/**
 * Wire WeeklyReportService over fakes for a route test. Builds the
 * fakes' contents from supplied seeds, then constructs the dependent
 * services so the WeeklyReportService captured into [ApiFixture] sees
 * the seeded repos rather than the empty defaults.
 */
@Suppress("LongParameterList", "LongMethod")
private fun reportFixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    initialTeams: List<Team> = emptyList(),
    initialGolfers: List<Golfer> = emptyList(),
    initialRosters: List<RosterEntry> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    initialScores: List<FantasyScore> = emptyList(),
    espn: EspnClient = FakeEspnClient(),
    reportTx: Transactor = FakeTransactor(),
): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        if (seedSeason) {
            kotlinx.coroutines.runBlocking {
                with(noopTransactionContext) {
                    seasonRepo.create(
                        CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                    )
                }
            }
        }
        val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
        initialResults.forEach { result ->
            kotlinx.coroutines.runBlocking {
                with(noopTransactionContext) {
                    tournamentRepo.upsertResult(result.tournamentId, asUpsertRequest(result))
                }
            }
        }
        val teamRepo = FakeTeamRepository(initialTeams = initialTeams, initialRoster = initialRosters)
        val golferRepo = FakeGolferRepository(initial = initialGolfers)
        val scoringRepo = FakeScoringRepository(initialScores = initialScores)
        seasonService = SeasonService(seasonRepo, FakeTransactor())
        tournamentService = TournamentService(tournamentRepo, FakeTransactor())
        teamService = TeamService(teamRepo, FakeTransactor())
        golferService = GolferService(golferRepo, FakeTransactor())
        scoringService =
            ScoringService(
                repository = scoringRepo,
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                tx = FakeTransactor(),
            )
        val sharedTx = FakeTransactor()
        val linkRepo = FakeTournamentLinkRepository()
        val previewEspnService =
            testEspnService(
                client = espn,
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
                linkRepository = linkRepo,
                tx = sharedTx,
            )
        liveOverlayService = LiveOverlayService(previewEspnService)
        weeklyReportService =
            WeeklyReportService(
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                golferRepository = golferRepo,
                scoringRepository = scoringRepo,
                liveOverlayService = liveOverlayService,
                tx = reportTx,
            )
    }

// A read is a service call only while every report method opens exactly one, on a transactor nothing else shares.
private class ReadCountingTransactor(
    private val delegate: Transactor = FakeTransactor(),
) : Transactor by delegate {
    private val counted = AtomicInteger()
    val reads: Int get() = counted.get()

    override suspend fun <A> read(block: context(TransactionContext) () -> A): A {
        counted.incrementAndGet()
        return delegate.read(block)
    }
}

private class FailsFirstFetchEspnClient(
    private val recovered: List<EspnTournament>,
) : EspnClient {
    private val fetches = AtomicInteger()

    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        if (fetches.getAndIncrement() == 0) throw EspnUpstreamException(503, "Service Unavailable")
        return recovered
    }

    override suspend fun fetchCalendar(): List<EspnCalendarEntry> = emptyList()
}

private fun asUpsertRequest(r: TournamentResult): CreateTournamentResultRequest =
    CreateTournamentResultRequest(
        golferId = r.golferId,
        position = r.position,
        scoreToPar = r.scoreToPar,
        totalStrokes = r.totalStrokes,
        earnings = r.earnings,
        round1 = r.round1,
        round2 = r.round2,
        round3 = r.round3,
        round4 = r.round4,
        madeCut = r.madeCut,
        pairKey = r.pairKey,
    )

// Reads the raw body: a typed decode shares the encoder's @SerialName, so it cannot pin the wire spelling.
private fun wireScope(body: String): JsonElement? = appJson.parseToJsonElement(body).jsonObject["scope"]

// Raw for the reason [wireScope] is, and read as numbers: a sum's scale is no part of the wire contract.
private fun wireTotals(
    body: String,
    field: String,
): Map<String, Double> =
    appJson.parseToJsonElement(body).jsonObject.getValue(field).jsonObject.mapValues { it.value.jsonPrimitive.double }

private fun wireReport(body: String): JsonObject = appJson.parseToJsonElement(body).jsonObject

// No team here has a number, so the report falls back to listing them by name: [team] is an alphabetical index.
private fun wireCell(
    report: JsonObject,
    team: Int,
    round: Int,
): JsonObject = report.getValue("teams").jsonArray[team].jsonObject.getValue("cells").jsonArray[round - 1].jsonObject

private fun wireFinish(
    position: Int,
    tied: Boolean,
): JsonObject =
    buildJsonObject {
        put("position", position)
        put("tied", tied)
    }

private fun wirePlayer(
    name: String,
    vararg teamNames: String,
): JsonObject =
    buildJsonObject {
        put("name", name)
        putJsonArray("team_names") { teamNames.forEach { add(it) } }
    }

private fun wireLeaderboard(body: String): List<JsonObject> =
    wireReport(body).getValue("live_leaderboard").jsonArray.map { it.jsonObject }

private val SCHEFFLER = golferRow(SCOTTIE_ID, "Scottie", "Scheffler")

private val MASTERS_GOLFERS =
    listOf(SCHEFFLER, golferRow(RORY_ID, "Rory", "McIlroy"), golferRow(XANDER_ID, "Xander", "Schauffele"))

private val MASTERS_IN_PLAY =
    tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
        .copy(status = TournamentStatus.InProgress, pgaTournamentId = "espn-masters")

private fun espnMasters(field: List<EspnCompetitor>): EspnTournament =
    EspnTournament(
        espnId = "espn-masters",
        name = "The Masters",
        completed = false,
        competitors = field,
        isTeamEvent = false,
    )

private fun espnLeader(golfer: Golfer): EspnCompetitor =
    EspnCompetitor(
        espnId = golfer.pgaPlayerId.orEmpty(),
        name = "${golfer.firstName} ${golfer.lastName}",
        order = 1,
        scoreStr = "-5",
        scoreToPar = -5,
        totalStrokes = null,
        roundScores = emptyList(),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

class ReportRoutesSpec : FunSpec({

    // ----- GET /api/v1/seasons/{id}/report/{tournamentId} -----

    test("GET /api/v1/seasons/{id}/report/{tid} returns 200 with the weekly report body") {
        val masters = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
        apiTest(reportFixture(initialTournaments = listOf(masters))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}")
            response.status shouldBe HttpStatusCode.OK
            val body: WeeklyReport = response.body()
            body.tournament.name shouldBe "The Masters"
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid} puts scope \"tournament\" on the wire") {
        val masters = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
        apiTest(reportFixture(initialTournaments = listOf(masters))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}")
            wireScope(response.bodyAsText()) shouldBe JsonPrimitive("tournament")
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid} returns 404 when the season doesn't exist") {
        apiTest(reportFixture(seedSeason = false)) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid} returns 404 when the tournament doesn't exist") {
        apiTest(reportFixture()) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/seasons/{non-uuid}/report/{tid} returns 400") {
        apiTest(reportFixture()) { client ->
            val response = client.get("/api/v1/seasons/not-a-uuid/report/${MASTERS_ID.value}")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid}?live=true is accepted (currently a no-op)") {
        val masters = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
        apiTest(reportFixture(initialTournaments = listOf(masters))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}?live=true")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    // ----- GET /api/v1/seasons/{id}/report -----

    test("GET /api/v1/seasons/{id}/report (season aggregate) returns 200 with an All Tournaments header") {
        apiTest(reportFixture(initialTeams = listOf(teamRow(TEAM_A, "BROWN")))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report")
            response.status shouldBe HttpStatusCode.OK
            val body: WeeklyReport = response.body()
            body.tournament.name shouldBe "All Tournaments"
            body.tournament.id shouldBe null
        }
    }

    test("GET /api/v1/seasons/{id}/report puts scope \"season\" on the wire") {
        apiTest(reportFixture(initialTeams = listOf(teamRow(TEAM_A, "BROWN")))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report")
            wireScope(response.bodyAsText()) shouldBe JsonPrimitive("season")
        }
    }

    test("GET /api/v1/seasons/{id}/report puts the side-bet rules, team names and outcomes on the wire") {
        val seeded =
            reportFixture(
                initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers =
                    listOf(golferRow(SCOTTIE_ID, "Scottie", "Scheffler"), golferRow(RORY_ID, "Rory", "McIlroy")),
                initialRosters = listOf(rosterRow(TEAM_A, SCOTTIE_ID, 5), rosterRow(TEAM_B, RORY_ID, 5)),
                initialScores = listOf(scoreRow(TEAM_A, MASTERS_ID, SCOTTIE_ID, BigDecimal(18))),
            )
        apiTest(seeded) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report")
            val report = appJson.parseToJsonElement(response.bodyAsText()).jsonObject

            report["side_bet_rules"] shouldBe
                buildJsonObject {
                    put("amount_per_team", 15)
                    putJsonArray("rounds") { listOf(5, 6, 7, 8).forEach { add(it) } }
                }
            val teamsByRound =
                report.getValue("side_bet_detail").jsonArray.map { round ->
                    round.jsonObject.getValue("teams").jsonArray.map { it.jsonObject }
                }
            teamsByRound[0].map { it["team_name"] to it["outcome"] } shouldContainExactly
                listOf(JsonPrimitive("BROWN") to JsonPrimitive("won"), JsonPrimitive("WOMBLE") to JsonPrimitive("lost"))
            teamsByRound[1].map { it["outcome"] } shouldContainExactly listOf(JsonNull, JsonNull)
        }
    }

    test("GET /api/v1/seasons/{id}/report returns 404 when the season doesn't exist") {
        apiTest(reportFixture(seedSeason = false)) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    // ----- report totals: both scopes carry them -----

    listOf(
        "GET /api/v1/seasons/{id}/report/{tid}" to "/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}",
        "GET /api/v1/seasons/{id}/report" to "/api/v1/seasons/${SEASON_ID.value}/report",
    ).forEach { (route, url) ->
        test("$route puts the cash, side-bet and weekly totals on the wire") {
            val brownOutEarnsWomble =
                reportFixture(
                    initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                    initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                    initialGolfers =
                        listOf(golferRow(SCOTTIE_ID, "Scottie", "Scheffler"), golferRow(RORY_ID, "Rory", "McIlroy")),
                    initialRosters = listOf(rosterRow(TEAM_A, SCOTTIE_ID, 5), rosterRow(TEAM_B, RORY_ID, 5)),
                    initialScores = listOf(scoreRow(TEAM_A, MASTERS_ID, SCOTTIE_ID, BigDecimal(18))),
                )
            apiTest(brownOutEarnsWomble) { client ->
                val body = client.get(url).bodyAsText()

                // WOMBLE pays BROWN the 18 its pick earned and the default 15 side bet on round 5.
                wireTotals(body, "cash_totals") shouldBe mapOf("won" to 33.0, "lost" to -33.0, "net" to 0.0)
                wireTotals(body, "side_bet_totals") shouldBe mapOf("won" to 15.0, "lost" to -15.0, "net" to 0.0)
                wireTotals(body, "weekly_totals") shouldBe mapOf("won" to 18.0, "lost" to -18.0, "net" to 0.0)
            }
        }
    }

    // ----- typed finish and score to par -----

    test("GET /api/v1/seasons/{id}/report/{tid} sends a cell's finish as a position and a tie flag") {
        val sharedThird =
            reportFixture(
                initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers =
                    listOf(
                        golferRow(SCOTTIE_ID, "Scottie", "Scheffler"),
                        golferRow(RORY_ID, "Rory", "McIlroy"),
                        golferRow(XANDER_ID, "Xander", "Schauffele"),
                    ),
                initialRosters =
                    listOf(
                        rosterRow(TEAM_A, SCOTTIE_ID, draftRound = 5),
                        rosterRow(TEAM_B, RORY_ID, draftRound = 5),
                    ),
                initialResults =
                    listOf(
                        resultRow(MASTERS_ID, SCOTTIE_ID, position = 1).copy(scoreToPar = -5),
                        resultRow(MASTERS_ID, RORY_ID, position = 3).copy(scoreToPar = 0),
                        resultRow(MASTERS_ID, XANDER_ID, position = 3).copy(scoreToPar = 0),
                    ),
                initialScores = listOf(scoreRow(TEAM_A, MASTERS_ID, SCOTTIE_ID, BigDecimal(18))),
            )
        apiTest(sharedThird) { client ->
            val body = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}").bodyAsText()
            val report = wireReport(body)

            wireCell(report, team = 0, round = 5)["finish"] shouldBe wireFinish(position = 1, tied = false)
            wireCell(report, team = 1, round = 5)["finish"] shouldBe wireFinish(position = 3, tied = true)
            wireCell(report, team = 0, round = 1)["finish"] shouldBe JsonNull
        }
    }

    test("GET /api/v1/seasons/{id}/report sends no finish on a cell whose position_str counts top tens") {
        val sharedThird =
            reportFixture(
                initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers =
                    listOf(
                        golferRow(SCOTTIE_ID, "Scottie", "Scheffler"),
                        golferRow(RORY_ID, "Rory", "McIlroy"),
                        golferRow(XANDER_ID, "Xander", "Schauffele"),
                    ),
                initialRosters =
                    listOf(
                        rosterRow(TEAM_A, SCOTTIE_ID, draftRound = 5),
                        rosterRow(TEAM_B, RORY_ID, draftRound = 5),
                    ),
                initialResults =
                    listOf(
                        resultRow(MASTERS_ID, SCOTTIE_ID, position = 1).copy(scoreToPar = -5),
                        resultRow(MASTERS_ID, RORY_ID, position = 3).copy(scoreToPar = 0),
                        resultRow(MASTERS_ID, XANDER_ID, position = 3).copy(scoreToPar = 0),
                    ),
                initialScores = listOf(scoreRow(TEAM_A, MASTERS_ID, SCOTTIE_ID, BigDecimal(18))),
            )
        apiTest(sharedThird) { client ->
            val body = client.get("/api/v1/seasons/${SEASON_ID.value}/report").bodyAsText()
            val scheffler = wireCell(wireReport(body), team = 0, round = 5)

            scheffler["position_str"] shouldBe JsonPrimitive("1x")
            scheffler["finish"] shouldBe JsonNull
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid} sends every score to par as a number") {
        val sharedThird =
            reportFixture(
                initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers =
                    listOf(
                        golferRow(SCOTTIE_ID, "Scottie", "Scheffler"),
                        golferRow(RORY_ID, "Rory", "McIlroy"),
                        golferRow(XANDER_ID, "Xander", "Schauffele"),
                    ),
                initialRosters =
                    listOf(
                        rosterRow(TEAM_A, SCOTTIE_ID, draftRound = 5),
                        rosterRow(TEAM_B, RORY_ID, draftRound = 5),
                    ),
                initialResults =
                    listOf(
                        resultRow(MASTERS_ID, SCOTTIE_ID, position = 1).copy(scoreToPar = -5),
                        resultRow(MASTERS_ID, RORY_ID, position = 3).copy(scoreToPar = 0),
                        resultRow(MASTERS_ID, XANDER_ID, position = 3).copy(scoreToPar = 0),
                    ),
                initialScores = listOf(scoreRow(TEAM_A, MASTERS_ID, SCOTTIE_ID, BigDecimal(18))),
            )
        apiTest(sharedThird) { client ->
            val body = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}").bodyAsText()
            val report = wireReport(body)
            val evenPar = JsonPrimitive(0)

            wireCell(report, team = 0, round = 5)["score_to_par"] shouldBe JsonPrimitive(-5)
            wireCell(report, team = 1, round = 5)["score_to_par"] shouldBe evenPar
            wireCell(report, team = 0, round = 1)["score_to_par"] shouldBe JsonNull
            report.getValue("undrafted_top_tens").jsonArray.map { it.jsonObject["score_to_par"] } shouldContainExactly
                listOf(evenPar)
            report.getValue("live_leaderboard").jsonArray.map { it.jsonObject["score_to_par"] } shouldContainExactly
                listOf(JsonPrimitive(-5), evenPar, evenPar)
        }
    }

    // ----- leaderboard rows: built on the server, pairs collapsed -----

    test("GET /api/v1/seasons/{id}/report/{tid} lists each leaderboard row's players with the teams rostering them") {
        val sharedThird =
            reportFixture(
                initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers = MASTERS_GOLFERS,
                initialRosters =
                    listOf(
                        rosterRow(TEAM_A, SCOTTIE_ID, draftRound = 5),
                        rosterRow(TEAM_B, RORY_ID, draftRound = 5),
                    ),
                initialResults =
                    listOf(
                        resultRow(MASTERS_ID, SCOTTIE_ID, position = 1),
                        resultRow(MASTERS_ID, RORY_ID, position = 3),
                        resultRow(MASTERS_ID, XANDER_ID, position = 3),
                    ),
            )
        apiTest(sharedThird) { client ->
            val body = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}").bodyAsText()
            val rows = wireLeaderboard(body)

            // The two thirds arrive in the repository's order, which this does not pin.
            rows.map { it["players"]?.jsonArray?.single() to it["rostered"] } shouldContainExactlyInAnyOrder
                listOf(
                    wirePlayer("Scottie Scheffler", "BROWN") to JsonPrimitive(true),
                    wirePlayer("Rory McIlroy", "WOMBLE") to JsonPrimitive(true),
                    wirePlayer("Xander Schauffele") to JsonPrimitive(false),
                )
            rows.map { it.keys }.distinct() shouldContainExactly
                listOf(setOf("position", "score_to_par", "players", "round_scores", "total_strokes", "rostered"))
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid} sends a team event's partners as one leaderboard row") {
        val partnersThenASolo =
            listOf(
                resultRow(MASTERS_ID, SCOTTIE_ID, position = 1).copy(pairKey = "team:1"),
                resultRow(MASTERS_ID, RORY_ID, position = 1).copy(pairKey = "team:1"),
                resultRow(MASTERS_ID, XANDER_ID, position = 3),
            )
        val configure =
            reportFixture(
                initialTournaments = listOf(tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers = MASTERS_GOLFERS,
                initialRosters = listOf(rosterRow(TEAM_A, SCOTTIE_ID, 5)),
                initialResults = partnersThenASolo,
            )
        apiTest(configure) { client ->
            val body = client.get("/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}").bodyAsText()
            val rows = wireLeaderboard(body)

            // Partners share a position, and the repository breaks that tie by a result id no fixture here pins.
            rows.map { it["players"]?.jsonArray?.toSet() } shouldContainExactly
                listOf(
                    setOf(wirePlayer("Scottie Scheffler", "BROWN"), wirePlayer("Rory McIlroy")),
                    setOf(wirePlayer("Xander Schauffele")),
                )
            rows.map { it["rostered"] } shouldContainExactly listOf(JsonPrimitive(true), JsonPrimitive(false))
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid}?live=true names every team rostering a live leaderboard player") {
        val schefflerOnTheCourse = espnMasters(field = listOf(espnLeader(SCHEFFLER)))
        val configure =
            reportFixture(
                initialTournaments = listOf(MASTERS_IN_PLAY),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers = MASTERS_GOLFERS,
                initialRosters =
                    listOf(
                        rosterRow(TEAM_A, SCOTTIE_ID, draftRound = 5),
                        rosterRow(TEAM_B, SCOTTIE_ID, draftRound = 5),
                    ),
                espn =
                    FakeEspnClient(
                        tournamentsByDate =
                            mapOf(MASTERS_IN_PLAY.startDate to listOf(schefflerOnTheCourse)),
                    ),
            )
        apiTest(configure) { client ->
            val url = "/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}?live=true"
            val rows = wireLeaderboard(client.get(url).bodyAsText())

            rows.map { it["players"]?.jsonArray?.toList() } shouldContainExactly
                listOf(listOf(wirePlayer("Scottie Scheffler", "BROWN", "WOMBLE")))
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid}?live=true keeps the persisted leaderboard when ESPN's field is empty") {
        val noField = espnMasters(field = emptyList())
        val configure =
            reportFixture(
                initialTournaments = listOf(MASTERS_IN_PLAY),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers = MASTERS_GOLFERS,
                initialRosters = listOf(rosterRow(TEAM_A, SCOTTIE_ID, draftRound = 5)),
                initialResults = listOf(resultRow(MASTERS_ID, SCOTTIE_ID, position = 1)),
                espn = FakeEspnClient(tournamentsByDate = mapOf(MASTERS_IN_PLAY.startDate to listOf(noField))),
            )
        apiTest(configure) { client ->
            val url = "/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}?live=true"
            val body = client.get(url).bodyAsText()

            wireReport(body)["live"] shouldBe JsonPrimitive(true)
            wireLeaderboard(body).map { it["position"] } shouldContainExactly listOf(JsonPrimitive(1))
        }
    }

    // ----- GET /api/v1/seasons/{id}/rankings -----

    test("GET /api/v1/seasons/{id}/rankings returns 200 with team rankings + per-tournament series") {
        val sony = tournamentRow(SONY_ID, "Sony Open", "2026-01-15", week = "2")
        val masters = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09", week = "15")
        val winner = golferRow(SCOTTIE_ID, "Scottie", "Scheffler")
        val configure =
            reportFixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
                initialGolfers = listOf(winner),
                initialRosters = listOf(rosterRow(TEAM_A, winner.id, draftRound = 1)),
                initialScores =
                    listOf(
                        scoreRow(TEAM_A, SONY_ID, winner.id, BigDecimal(18)),
                        scoreRow(TEAM_A, MASTERS_ID, winner.id, BigDecimal(18)),
                    ),
            )

        apiTest(configure) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/rankings")
            response.status shouldBe HttpStatusCode.OK
            val body: Rankings = response.body()
            body.tournamentNames shouldContainExactly listOf("Sony Open", "The Masters")
            body.weeks shouldContainExactly listOf("2", "15")
        }
    }

    test("GET /api/v1/seasons/{id}/rankings?through= trims the included tournaments") {
        val sony = tournamentRow(SONY_ID, "Sony Open", "2026-01-15")
        val masters = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
        val configure =
            reportFixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(teamRow(TEAM_A, "BROWN"), teamRow(TEAM_B, "WOMBLE")),
            )

        apiTest(configure) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/rankings?through=${SONY_ID.value}")
            response.status shouldBe HttpStatusCode.OK
            response.body<Rankings>().tournamentNames shouldContainExactly listOf("Sony Open")
        }
    }

    test("GET /api/v1/seasons/{id}/rankings?through={non-uuid} returns 400") {
        apiTest(reportFixture()) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/rankings?through=not-a-uuid")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/seasons/{id}/rankings?through={unknown} returns 404") {
        apiTest(reportFixture()) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/rankings?through=${MASTERS_ID.value}")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    // ----- response caching: live requests bypass it, the rest are served from it -----

    LIVE_CAPABLE_PATHS.forEach { (label, path) ->
        val url = "/api/v1/seasons/${SEASON_ID.value}/$path"

        test("GET /api/v1/seasons/{id}/$label?live=true reaches the service on every request") {
            val reportTx = ReadCountingTransactor()
            val espn = FakeEspnClient()
            val masters =
                tournamentRow(MASTERS_ID, "The Masters", "2026-04-09").copy(status = TournamentStatus.InProgress)
            val configure = reportFixture(initialTournaments = listOf(masters), espn = espn, reportTx = reportTx)
            apiTest(configure) { client ->
                repeat(2) { client.get("$url?live=true").status shouldBe HttpStatusCode.OK }
                reportTx.reads shouldBe 2
                espn.fetchCalls shouldContainExactly listOf(masters.startDate, masters.startDate)
            }
        }

        test("GET /api/v1/seasons/{id}/$label?live=true is served from the response cache between events") {
            val reportTx = ReadCountingTransactor()
            val finished = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
            val beyondLookahead =
                tournamentRow(SONY_ID, "Sony Open", LocalDate.now(ESPN_SCHEDULE_ZONE).plusWeeks(2).toString())
                    .copy(status = TournamentStatus.Upcoming)
            val configure = reportFixture(initialTournaments = listOf(finished, beyondLookahead), reportTx = reportTx)
            apiTest(configure) { client ->
                repeat(2) { client.get("$url?live=true").status shouldBe HttpStatusCode.OK }
                reportTx.reads shouldBe 1
            }
        }

        test("GET /api/v1/seasons/{id}/$label answers a repeat request from the response cache") {
            val reportTx = ReadCountingTransactor()
            val masters = tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
            apiTest(reportFixture(initialTournaments = listOf(masters), reportTx = reportTx)) { client ->
                repeat(2) { client.get(url).status shouldBe HttpStatusCode.OK }
                reportTx.reads shouldBe 1
            }
        }
    }

    test("GET /api/v1/seasons/{id}/report/{tid}?live=true asks ESPN again after a failed fetch") {
        val masters =
            tournamentRow(MASTERS_ID, "The Masters", "2026-04-09")
                .copy(status = TournamentStatus.InProgress, pgaTournamentId = "espn-masters")
        val liveMasters =
            EspnTournament(
                espnId = "espn-masters",
                name = "The Masters",
                completed = false,
                competitors = emptyList(),
                isTeamEvent = false,
            )
        // Behind the production ESPN cache, so the spec covers both layers a failure could be kept in.
        val espn =
            CachingEspnClient(
                delegate = FailsFirstFetchEspnClient(recovered = listOf(liveMasters)),
                scoreboardTtl = Duration.ofSeconds(60),
                scoreboardMaxSize = 8,
            )
        apiTest(reportFixture(initialTournaments = listOf(masters), espn = espn)) { client ->
            val url = "/api/v1/seasons/${SEASON_ID.value}/report/${MASTERS_ID.value}?live=true"
            client.get(url).body<WeeklyReport>().live shouldBe null
            client.get(url).body<WeeklyReport>().live shouldBe true
        }
    }

    // ----- GET /api/v1/seasons/{id}/golfer/{gid}/history -----

    test("GET /api/v1/seasons/{id}/golfer/{gid}/history returns 200 with the golfer's history") {
        val sony = tournamentRow(SONY_ID, "Sony Open", "2026-01-15")
        val scottie = golferRow(SCOTTIE_ID, "Scottie", "Scheffler")
        val configure =
            reportFixture(
                initialTournaments = listOf(sony),
                initialGolfers = listOf(scottie),
                initialResults = listOf(resultRow(SONY_ID, scottie.id, position = 1)),
            )

        apiTest(configure) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/golfer/${SCOTTIE_ID.value}/history")
            response.status shouldBe HttpStatusCode.OK
            val body: GolferHistory = response.body()
            body.golferName shouldBe "Scottie Scheffler"
            body.topTens shouldBe 1
            body.results.single().tournament shouldBe "Sony Open"
        }
    }

    test("GET /api/v1/seasons/{id}/golfer/{unknown}/history returns 404") {
        val unknown = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000999"))
        apiTest(reportFixture()) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/golfer/${unknown.value}/history")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/seasons/{id}/golfer/{non-uuid}/history returns 400") {
        apiTest(reportFixture()) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/golfer/not-a-uuid/history")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
