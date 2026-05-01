package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.leagues.LeagueId
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.Season
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
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
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val TEAM_A = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
private val GOLFER_1 = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000f01"))

private fun mkSeason(): Season =
    Season(
        id = SEASON_ID,
        leagueId = LEAGUE_ID,
        name = "2026",
        seasonYear = 2026,
        seasonNumber = 1,
        status = "active",
        tieFloor = SeasonRules.DEFAULT_TIE_FLOOR,
        sideBetAmount = SeasonRules.DEFAULT_SIDE_BET_AMOUNT,
        maxTeams = 10,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

private fun mkTournament(): Tournament =
    Tournament(
        id = TOURNAMENT_ID,
        pgaTournamentId = null,
        name = "Test Open",
        seasonId = SEASON_ID,
        startDate = LocalDate.parse("2026-04-01"),
        endDate = LocalDate.parse("2026-04-04"),
        courseName = null,
        status = TournamentStatus.Completed,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

private fun mkTeam(
    id: TeamId,
    name: String,
): Team =
    Team(
        id = id,
        seasonId = SEASON_ID,
        ownerName = "Owner $name",
        teamName = name,
        teamNumber = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

private fun mkRoster(
    teamId: TeamId,
    golferId: GolferId,
): RosterEntry =
    RosterEntry(
        id = RosterEntryId(UUID.randomUUID()),
        teamId = teamId,
        golferId = golferId,
        acquiredVia = "draft",
        draftRound = null,
        ownershipPct = BigDecimal(100),
        acquiredAt = Instant.EPOCH,
        droppedAt = null,
        isActive = true,
    )

private fun mkResult(
    golferId: GolferId,
    position: Int?,
): TournamentResult =
    TournamentResult(
        id = TournamentResultId(UUID.randomUUID()),
        tournamentId = TOURNAMENT_ID,
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

private data class RouteWorld(
    val season: Season? = mkSeason(),
    val tournament: Tournament? = mkTournament(),
    val teams: List<Team> = emptyList(),
    val rosters: List<RosterEntry> = emptyList(),
    val results: List<TournamentResult> = emptyList(),
    val initialScores: List<FantasyScore> = emptyList(),
)

private fun withWorld(world: RouteWorld = RouteWorld()): ApiFixture.() -> Unit =
    {
        val seasonRepo = FakeSeasonRepository(initial = listOfNotNull(world.season))
        val tournamentRepo =
            FakeTournamentRepository(initial = listOfNotNull(world.tournament), initialResults = world.results)
        val teamRepo = FakeTeamRepository(initialTeams = world.teams, initialRoster = world.rosters)
        seasonService = SeasonService(seasonRepo, FakeTransactor())
        tournamentService = TournamentService(tournamentRepo, FakeTransactor())
        teamService = TeamService(teamRepo, FakeTransactor())
        scoringService =
            ScoringService(
                repository = FakeScoringRepository(initialScores = world.initialScores),
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                tx = FakeTransactor(),
            )
    }

class ScoringRoutesSpec : FunSpec({

    test("GET /scoring/{tournamentId} returns persisted scores") {
        val score =
            FantasyScore(
                id = FantasyScoreId(UUID.randomUUID()),
                seasonId = SEASON_ID,
                teamId = TEAM_A,
                tournamentId = TOURNAMENT_ID,
                golferId = GOLFER_1,
                points = BigDecimal(18),
                position = 1,
                numTied = 1,
                basePayout = BigDecimal(18),
                ownershipPct = BigDecimal(100),
                payout = BigDecimal(18),
                multiplier = BigDecimal.ONE,
                calculatedAt = Instant.EPOCH,
            )
        apiTest(withWorld(RouteWorld(initialScores = listOf(score)))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/scoring/${TOURNAMENT_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            response.body<List<FantasyScore>>() shouldBe listOf(score)
        }
    }

    test("GET /scoring/{bad} returns 400 when the tournament id is malformed") {
        apiTest { client ->
            client.get("/api/v1/seasons/${SEASON_ID.value}/scoring/not-a-uuid")
                .status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /scoring/calculate/{tournamentId} returns 404 when the season does not exist") {
        authenticatedApiTest(withWorld(RouteWorld(season = null))) { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/calculate/${TOURNAMENT_ID.value}")
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /scoring/calculate/{tournamentId} returns 404 when the tournament does not exist") {
        authenticatedApiTest(withWorld(RouteWorld(tournament = null))) { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/calculate/${TOURNAMENT_ID.value}")
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /scoring/calculate/{tournamentId} returns 200 with the weekly result on success") {
        val team = mkTeam(TEAM_A, "Alice")
        authenticatedApiTest(
            withWorld(
                RouteWorld(
                    teams = listOf(team),
                    rosters = listOf(mkRoster(team.id, GOLFER_1)),
                    results = listOf(mkResult(GOLFER_1, 1)),
                ),
            ),
        ) { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/calculate/${TOURNAMENT_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            val weekly = response.body<WeeklyScoreResult>()
            weekly.numTeams shouldBe 1
            weekly.totalPot shouldBe BigDecimal(18)
        }
    }

    test("POST /scoring/calculate/{tournamentId} returns 401 without an authenticated session") {
        apiTest { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/calculate/${TOURNAMENT_ID.value}")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /scoring/refresh-standings returns 404 when the season does not exist") {
        authenticatedApiTest(withWorld(RouteWorld(season = null))) { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/refresh-standings")
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /scoring/refresh-standings returns 200 with a standing per team") {
        authenticatedApiTest(withWorld(RouteWorld(teams = listOf(mkTeam(TEAM_A, "Alice"))))) { client ->
            val response = client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/refresh-standings")

            response.status shouldBe HttpStatusCode.OK
            response.body<List<SeasonStanding>>().size shouldBe 1
        }
    }

    test("POST /scoring/refresh-standings returns 401 without an authenticated session") {
        apiTest { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/scoring/refresh-standings")
                .status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /scoring/side-bets returns 409 when the season has no teams") {
        apiTest(withWorld()) { client ->
            client.get("/api/v1/seasons/${SEASON_ID.value}/scoring/side-bets")
                .status shouldBe HttpStatusCode.Conflict
        }
    }

    test("GET /scoring/side-bets returns 404 when the season does not exist") {
        apiTest(withWorld(RouteWorld(season = null))) { client ->
            client.get("/api/v1/seasons/${SEASON_ID.value}/scoring/side-bets")
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /scoring/side-bets returns 200 when the season has teams") {
        apiTest(withWorld(RouteWorld(teams = listOf(mkTeam(TEAM_A, "Alice"))))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/scoring/side-bets")

            response.status shouldBe HttpStatusCode.OK
            // Default rules: 4 side-bet rounds; all inactive when no point totals exist.
            response.body<SideBetStandings>().rounds.size shouldBe 4
        }
    }

    test("GET /standings returns 200 with persisted standings") {
        apiTest(withWorld()) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/standings")

            response.status shouldBe HttpStatusCode.OK
            response.body<List<SeasonStanding>>() shouldBe emptyList()
        }
    }

    test("GET /standings returns 400 for a malformed season id") {
        apiTest { client ->
            client.get("/api/v1/seasons/not-a-uuid/standings").status shouldBe HttpStatusCode.BadRequest
        }
    }
})
