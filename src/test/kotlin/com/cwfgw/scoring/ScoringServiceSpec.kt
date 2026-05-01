package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
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
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))

private fun teamId(n: Int): TeamId = TeamId(UUID.fromString("00000000-0000-0000-0000-0000000000%02d".format(n)))

private fun golferId(n: Int): GolferId = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000f%02d".format(n)))

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

private fun mkTournament(multiplier: BigDecimal = BigDecimal.ONE): Tournament =
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
        payoutMultiplier = multiplier,
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
    ownership: BigDecimal = BigDecimal(100),
    draftRound: Int? = null,
): RosterEntry =
    RosterEntry(
        id = RosterEntryId(UUID.randomUUID()),
        teamId = teamId,
        golferId = golferId,
        acquiredVia = "draft",
        draftRound = draftRound,
        ownershipPct = ownership,
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

private data class World(
    val season: Season? = mkSeason(),
    val tournament: Tournament? = mkTournament(),
    val teams: List<Team> = emptyList(),
    val rosters: List<RosterEntry> = emptyList(),
    val results: List<TournamentResult> = emptyList(),
)

private data class FakeData(
    val pointTotals: Map<Triple<SeasonId, TeamId, GolferId>, BigDecimal> = emptyMap(),
    val teamTotals: Map<Pair<SeasonId, TeamId>, TeamSeasonTotals> = emptyMap(),
)

private class Fixture(
    world: World = World(),
    fakes: FakeData = FakeData(),
) {
    val scoring = FakeScoringRepository(pointTotals = fakes.pointTotals, teamTotals = fakes.teamTotals)
    val service: ScoringService

    init {
        val seasonRepo = FakeSeasonRepository(initial = listOfNotNull(world.season))
        val tournamentRepo =
            FakeTournamentRepository(
                initial = listOfNotNull(world.tournament),
                initialResults = world.results,
            )
        val teamRepo = FakeTeamRepository(initialTeams = world.teams, initialRoster = world.rosters)
        service =
            ScoringService(
                repository = scoring,
                seasonService = SeasonService(seasonRepo, FakeTransactor()),
                tournamentService = TournamentService(tournamentRepo, FakeTransactor()),
                teamService = TeamService(teamRepo, FakeTransactor()),
            )
    }
}

class ScoringServiceSpec : FunSpec({

    test("calculateScores returns SeasonNotFound when the season does not exist") {
        val fixture = Fixture(world = World(season = null))

        fixture.service.calculateScores(SEASON_ID, TOURNAMENT_ID) shouldBe Result.Err(ScoringError.SeasonNotFound)
    }

    test("calculateScores returns TournamentNotFound when the tournament does not exist") {
        val fixture = Fixture(world = World(tournament = null))

        fixture.service.calculateScores(SEASON_ID, TOURNAMENT_ID) shouldBe Result.Err(ScoringError.TournamentNotFound)
    }

    test("calculateScores yields zero-sum weekly totals across teams") {
        val team1 = mkTeam(teamId(1), "Alice")
        val team2 = mkTeam(teamId(2), "Bob")
        val fixture =
            Fixture(
                world =
                    World(
                        teams = listOf(team1, team2),
                        rosters = listOf(mkRoster(team1.id, golferId(1)), mkRoster(team2.id, golferId(2))),
                        results = listOf(mkResult(golferId(1), 1), mkResult(golferId(2), 11)),
                    ),
            )

        val weekly =
            fixture.service.calculateScores(SEASON_ID, TOURNAMENT_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyScoreResult>>()
                .value
        weekly.numTeams shouldBe 2
        weekly.totalPot shouldBe BigDecimal(18)
        val byTeam = weekly.teams.associateBy { it.teamId }
        byTeam.getValue(team1.id).topTens shouldBe BigDecimal(18)
        byTeam.getValue(team1.id).weeklyTotal shouldBe BigDecimal(18)
        byTeam.getValue(team2.id).topTens shouldBe BigDecimal.ZERO
        byTeam.getValue(team2.id).weeklyTotal shouldBe BigDecimal(-18)
    }

    test("calculateScores persists only golfers in the payout zone") {
        val team = mkTeam(teamId(1), "Alice")
        val rosters =
            listOf(
                mkRoster(team.id, golferId(1)),
                mkRoster(team.id, golferId(2)),
                mkRoster(team.id, golferId(3)),
            )
        val results = listOf(mkResult(golferId(1), null), mkResult(golferId(3), 50))
        val fixture = Fixture(world = World(teams = listOf(team), rosters = rosters, results = results))

        fixture.service.calculateScores(SEASON_ID, TOURNAMENT_ID).shouldBeInstanceOf<Result.Ok<WeeklyScoreResult>>()

        fixture.scoring.scoreUpserts.shouldHaveSize(0)
    }

    test("calculateScores applies the tournament payout multiplier to the pot") {
        val team1 = mkTeam(teamId(1), "Alice")
        val team2 = mkTeam(teamId(2), "Bob")
        val fixture =
            Fixture(
                world =
                    World(
                        tournament = mkTournament(multiplier = BigDecimal(2)),
                        teams = listOf(team1, team2),
                        rosters = listOf(mkRoster(team1.id, golferId(1))),
                        results = listOf(mkResult(golferId(1), 1)),
                    ),
            )

        val weekly =
            fixture.service.calculateScores(SEASON_ID, TOURNAMENT_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyScoreResult>>()
                .value
        weekly.multiplier shouldBe BigDecimal(2)
        weekly.totalPot shouldBe BigDecimal(36)
    }

    test("getSideBetStandings returns NoTeams when the season has none") {
        val fixture = Fixture()

        fixture.service.getSideBetStandings(SEASON_ID) shouldBe Result.Err(ScoringError.NoTeams)
    }

    test("getSideBetStandings returns SeasonNotFound when the season does not exist") {
        val fixture = Fixture(world = World(season = null, teams = listOf(mkTeam(teamId(1), "Alice"))))

        fixture.service.getSideBetStandings(SEASON_ID) shouldBe Result.Err(ScoringError.SeasonNotFound)
    }

    test("getSideBetStandings picks the highest-cumulative team as the winner of an active round") {
        val team1 = mkTeam(teamId(1), "Alice")
        val team2 = mkTeam(teamId(2), "Bob")
        val rosters =
            listOf(
                mkRoster(team1.id, golferId(1), draftRound = 5),
                mkRoster(team2.id, golferId(2), draftRound = 5),
            )
        val pointTotals =
            mapOf(
                Triple(SEASON_ID, team1.id, golferId(1)) to BigDecimal(30),
                Triple(SEASON_ID, team2.id, golferId(2)) to BigDecimal(10),
            )
        val fixture =
            Fixture(
                world = World(teams = listOf(team1, team2), rosters = rosters),
                fakes = FakeData(pointTotals = pointTotals),
            )

        val standings =
            fixture.service.getSideBetStandings(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<SideBetStandings>>()
                .value
        // Default sideBetRounds is [5, 6, 7, 8] — only round 5 has picks here.
        val round5 = standings.rounds.first { it.round == 5 }
        round5.active shouldBe true
        round5.winner?.teamId shouldBe team1.id
        round5.winner?.cumulativeEarnings shouldBe BigDecimal(30)
        // Winner collects sideBetAmount × (numTeams - 1) = 15 × 1 = 15
        val totalsByTeam = standings.teamTotals.associateBy { it.teamId }
        totalsByTeam.getValue(team1.id).net shouldBe BigDecimal(15)
        totalsByTeam.getValue(team1.id).wins shouldBe 1
        totalsByTeam.getValue(team2.id).net shouldBe BigDecimal(-15)
    }

    test("getSideBetStandings marks a round inactive when no team's pick has earned anything") {
        val team = mkTeam(teamId(1), "Alice")
        val rosters = listOf(mkRoster(team.id, golferId(1), draftRound = 5))
        val fixture = Fixture(world = World(teams = listOf(team), rosters = rosters))

        val standings =
            fixture.service.getSideBetStandings(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<SideBetStandings>>()
                .value
        val round5 = standings.rounds.first { it.round == 5 }
        round5.active shouldBe false
        round5.winner shouldBe null
        standings.teamTotals.first { it.teamId == team.id }.net shouldBe BigDecimal.ZERO
    }

    test("refreshStandings upserts a standing per team in the season") {
        val team1 = mkTeam(teamId(1), "Alice")
        val team2 = mkTeam(teamId(2), "Bob")
        val teamTotals =
            mapOf(
                (SEASON_ID to team1.id) to TeamSeasonTotals(BigDecimal(100), 5),
                (SEASON_ID to team2.id) to TeamSeasonTotals(BigDecimal(80), 5),
            )
        val fixture =
            Fixture(
                world = World(teams = listOf(team1, team2)),
                fakes = FakeData(teamTotals = teamTotals),
            )

        val result =
            fixture.service.refreshStandings(SEASON_ID).shouldBeInstanceOf<Result.Ok<List<SeasonStanding>>>().value
        result.shouldHaveSize(2)
        fixture.scoring.standingUpserts.shouldHaveSize(2)
        val upsertedByTeam = fixture.scoring.standingUpserts.associateBy { it.teamId }
        upsertedByTeam.getValue(team1.id).totalPoints shouldBe BigDecimal(100)
        upsertedByTeam.getValue(team1.id).tournamentsPlayed shouldBe 5
        upsertedByTeam.getValue(team2.id).totalPoints shouldBe BigDecimal(80)
    }

    test("refreshStandings returns SeasonNotFound when the season does not exist") {
        val fixture = Fixture(world = World(season = null))

        fixture.service.refreshStandings(SEASON_ID) shouldBe Result.Err(ScoringError.SeasonNotFound)
    }

    test("getScores delegates to the repository") {
        val team = mkTeam(teamId(1), "Alice")
        val score =
            FantasyScore(
                id = FantasyScoreId(UUID.randomUUID()),
                seasonId = SEASON_ID,
                teamId = team.id,
                tournamentId = TOURNAMENT_ID,
                golferId = golferId(1),
                points = BigDecimal(18),
                position = 1,
                numTied = 1,
                basePayout = BigDecimal(18),
                ownershipPct = BigDecimal(100),
                payout = BigDecimal(18),
                multiplier = BigDecimal.ONE,
                calculatedAt = Instant.EPOCH,
            )
        val scoring = FakeScoringRepository(initialScores = listOf(score))
        val service =
            ScoringService(
                repository = scoring,
                seasonService = SeasonService(FakeSeasonRepository(initial = listOf(mkSeason())), FakeTransactor()),
                tournamentService = TournamentService(FakeTournamentRepository(), FakeTransactor()),
                teamService = TeamService(FakeTeamRepository(), FakeTransactor()),
            )

        service.getScores(SEASON_ID, TOURNAMENT_ID) shouldBe listOf(score)
    }
})
