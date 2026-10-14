package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.Season
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
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
): RosterEntry =
    RosterEntry(
        id = RosterEntryId(UUID.randomUUID()),
        teamId = teamId,
        golferId = golferId,
        acquiredVia = "draft",
        draftRound = null,
        ownershipPct = ownership,
        acquiredAt = Instant.EPOCH,
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
    val teamTotals: Map<Pair<SeasonId, TeamId>, TeamSeasonTotals> = emptyMap(),
)

private class Fixture(
    world: World = World(),
    fakes: FakeData = FakeData(),
) {
    val scoring = FakeScoringRepository(teamTotals = fakes.teamTotals)
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
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
            )
    }
}

private fun Fixture.calculateScores(): Result<WeeklyScoreResult, ScoringError> =
    with(noopTransactionContext) { service.calculateScoresIn(SEASON_ID, TOURNAMENT_ID) }

private fun Fixture.refreshStandings(): Result<List<SeasonStanding>, ScoringError> =
    with(noopTransactionContext) { service.refreshStandingsIn(SEASON_ID) }

class ScoringServiceSpec : FunSpec({

    test("calculateScoresIn returns SeasonNotFound when the season does not exist") {
        val fixture = Fixture(world = World(season = null))

        fixture.calculateScores() shouldBe Result.Err(ScoringError.SeasonNotFound)
    }

    test("calculateScoresIn returns TournamentNotFound when the tournament does not exist") {
        val fixture = Fixture(world = World(tournament = null))

        fixture.calculateScores() shouldBe Result.Err(ScoringError.TournamentNotFound)
    }

    test("calculateScoresIn yields zero-sum weekly totals across teams") {
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

        val weekly = fixture.calculateScores().orFail()
        weekly.numTeams shouldBe 2
        weekly.totalPot shouldBe BigDecimal(18)
        val byTeam = weekly.teams.associateBy { it.teamId }
        byTeam.getValue(team1.id).topTens shouldBe BigDecimal(18)
        byTeam.getValue(team1.id).weeklyTotal shouldBe BigDecimal(18)
        byTeam.getValue(team2.id).topTens shouldBe BigDecimal.ZERO
        byTeam.getValue(team2.id).weeklyTotal shouldBe BigDecimal(-18)
    }

    test("calculateScoresIn persists only golfers in the payout zone") {
        val team = mkTeam(teamId(1), "Alice")
        val rosters =
            listOf(
                mkRoster(team.id, golferId(1)),
                mkRoster(team.id, golferId(2)),
                mkRoster(team.id, golferId(3)),
            )
        val results = listOf(mkResult(golferId(1), null), mkResult(golferId(3), 50))
        val fixture = Fixture(world = World(teams = listOf(team), rosters = rosters, results = results))

        fixture.calculateScores().shouldBeInstanceOf<Result.Ok<WeeklyScoreResult>>()

        fixture.scoring.scoreUpserts.shouldHaveSize(0)
    }

    test("calculateScoresIn applies the tournament payout multiplier to the pot") {
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

        val weekly = fixture.calculateScores().orFail()
        weekly.multiplier shouldBe BigDecimal(2)
        weekly.totalPot shouldBe BigDecimal(36)
    }

    test("refreshStandingsIn upserts a standing per team in the season") {
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

        val result = fixture.refreshStandings().orFail()
        result.shouldHaveSize(2)
        fixture.scoring.standingUpserts.shouldHaveSize(2)
        val upsertedByTeam = fixture.scoring.standingUpserts.associateBy { it.teamId }
        upsertedByTeam.getValue(team1.id).totalPoints shouldBe BigDecimal(100)
        upsertedByTeam.getValue(team1.id).tournamentsPlayed shouldBe 5
        upsertedByTeam.getValue(team2.id).totalPoints shouldBe BigDecimal(80)
    }

    test("refreshStandingsIn returns SeasonNotFound when the season does not exist") {
        val fixture = Fixture(world = World(season = null))

        fixture.refreshStandings() shouldBe Result.Err(ScoringError.SeasonNotFound)
    }
})
