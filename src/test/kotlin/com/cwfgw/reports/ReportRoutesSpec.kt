package com.cwfgw.reports

import com.cwfgw.espn.EspnService
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
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
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val TEAM_A = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val TEAM_B = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c02"))
private val SCOTTIE_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))

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
@Suppress("LongParameterList")
private fun reportFixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    initialTeams: List<Team> = emptyList(),
    initialGolfers: List<Golfer> = emptyList(),
    initialRosters: List<RosterEntry> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    initialScores: List<FantasyScore> = emptyList(),
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
        val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
        initialResults.forEach { result ->
            kotlinx.coroutines.runBlocking {
                tournamentRepo.upsertResult(result.tournamentId, asUpsertRequest(result))
            }
        }
        val teamRepo = FakeTeamRepository(initialTeams = initialTeams, initialRoster = initialRosters)
        val golferRepo = FakeGolferRepository(initial = initialGolfers)
        val scoringRepo = FakeScoringRepository(initialScores = initialScores)
        seasonService = SeasonService(seasonRepo)
        tournamentService = TournamentService(tournamentRepo)
        teamService = TeamService(teamRepo, FakeTransactor())
        golferService = GolferService(golferRepo, FakeTransactor())
        scoringService =
            ScoringService(
                repository = scoringRepo,
                seasonService = seasonService,
                tournamentService = tournamentService,
                teamService = teamService,
            )
        val previewEspnService =
            EspnService(
                client = FakeEspnClient(),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
                seasonService = seasonService,
                tournamentLinkRepository = FakeTournamentLinkRepository(),
            )
        liveOverlayService = LiveOverlayService(previewEspnService)
        weeklyReportService =
            WeeklyReportService(
                seasonService = seasonService,
                tournamentService = tournamentService,
                teamService = teamService,
                golferService = golferService,
                scoringService = scoringService,
                liveOverlayService = liveOverlayService,
            )
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

    test("GET /api/v1/seasons/{id}/report returns 404 when the season doesn't exist") {
        apiTest(reportFixture(seedSeason = false)) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/report")
            response.status shouldBe HttpStatusCode.NotFound
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
