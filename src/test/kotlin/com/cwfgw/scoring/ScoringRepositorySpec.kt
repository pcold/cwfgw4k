package com.cwfgw.scoring

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class ScoringRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = ScoringRepository(postgres.dsl)
    val leagueRepo = LeagueRepository(postgres.dsl)
    val seasonRepo = SeasonRepository()
    val teamRepo = TeamRepository()
    val tournamentRepo = TournamentRepository(postgres.dsl)
    val golferRepo = GolferRepository()
    val tx = Transactor(postgres.dsl)
    var seasonId = SeasonId(UUID.randomUUID())
    var teamA = TeamId(UUID.randomUUID())
    var teamB = TeamId(UUID.randomUUID())
    var tournamentA = TournamentId(UUID.randomUUID())
    var tournamentB = TournamentId(UUID.randomUUID())
    var golfer1 = GolferId(UUID.randomUUID())
    var golfer2 = GolferId(UUID.randomUUID())

    beforeEach {
        val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
        seasonId =
            tx.update {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026),
                )
            }.id
        tx.update {
            teamA = teamRepo.create(seasonId, CreateTeamRequest(ownerName = "Alice", teamName = "Alpha")).id
            teamB = teamRepo.create(seasonId, CreateTeamRequest(ownerName = "Bob", teamName = "Bravo")).id
        }
        tournamentA =
            tournamentRepo.create(
                CreateTournamentRequest(
                    name = "Open A",
                    seasonId = seasonId,
                    startDate = LocalDate.parse("2026-04-01"),
                    endDate = LocalDate.parse("2026-04-04"),
                ),
            ).id
        tournamentB =
            tournamentRepo.create(
                CreateTournamentRequest(
                    name = "Open B",
                    seasonId = seasonId,
                    startDate = LocalDate.parse("2026-04-08"),
                    endDate = LocalDate.parse("2026-04-11"),
                ),
            ).id
        tx.update {
            golfer1 = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy")).id
            golfer2 = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler")).id
        }
    }

    fun breakdown(
        position: Int,
        numTied: Int = 1,
        ownership: BigDecimal = BigDecimal(100),
        payout: BigDecimal = BigDecimal(18),
    ): ScoreBreakdown =
        ScoreBreakdown(
            position = position,
            numTied = numTied,
            basePayout = payout,
            ownershipPct = ownership,
            payout = payout,
            multiplier = BigDecimal.ONE,
        )

    suspend fun upsert(
        team: TeamId,
        tournament: TournamentId,
        golfer: GolferId,
        breakdown: ScoreBreakdown,
    ): FantasyScore =
        repository.upsertScore(
            UpsertScore(
                seasonId = seasonId,
                teamId = team,
                tournamentId = tournament,
                golferId = golfer,
                breakdown = breakdown,
            ),
        )

    test("upsertScore inserts then updates the same row on conflict") {
        val first = upsert(teamA, tournamentA, golfer1, breakdown(1))
        val second = upsert(teamA, tournamentA, golfer1, breakdown(3, 2, payout = BigDecimal(9)))

        first.id shouldBe second.id
        second.points.compareTo(BigDecimal(9)) shouldBe 0
        second.position shouldBe 3
        second.numTied shouldBe 2
    }

    test("getScores returns rows for the given season+tournament ordered by points desc") {
        upsert(teamA, tournamentA, golfer1, breakdown(1))
        upsert(teamB, tournamentA, golfer2, breakdown(2, payout = BigDecimal(12)))
        upsert(teamA, tournamentB, golfer1, breakdown(4, payout = BigDecimal(7)))

        val scores = repository.getScores(seasonId, tournamentA)

        scores.shouldHaveSize(2)
        scores.map { it.points.compareTo(BigDecimal(18)) }.first() shouldBe 0
        scores.map { it.golferId } shouldBe listOf(golfer1, golfer2)
    }

    test("golferPointTotal sums points across tournaments for a team+golfer") {
        upsert(teamA, tournamentA, golfer1, breakdown(1))
        upsert(teamA, tournamentB, golfer1, breakdown(4, payout = BigDecimal(7)))
        // Different team's points should be excluded
        upsert(teamB, tournamentA, golfer1, breakdown(1, payout = BigDecimal(99)))

        repository.golferPointTotal(seasonId, teamA, golfer1).compareTo(BigDecimal(25)) shouldBe 0
    }

    test("teamSeasonTotals sums points and counts distinct tournaments") {
        upsert(teamA, tournamentA, golfer1, breakdown(1))
        upsert(teamA, tournamentA, golfer2, breakdown(2, payout = BigDecimal(12)))
        upsert(teamA, tournamentB, golfer1, breakdown(4, payout = BigDecimal(7)))

        val totals = repository.teamSeasonTotals(seasonId, teamA)

        totals.totalPoints.compareTo(BigDecimal(37)) shouldBe 0
        totals.tournamentsPlayed shouldBe 2
    }

    test("teamSeasonTotals returns zeros when no scores exist") {
        val totals = repository.teamSeasonTotals(seasonId, teamA)

        totals.totalPoints.compareTo(BigDecimal.ZERO) shouldBe 0
        totals.tournamentsPlayed shouldBe 0
    }

    test("upsertStanding inserts then updates the same row on conflict") {
        val first = repository.upsertStanding(seasonId, teamA, BigDecimal(50), 3)
        val second = repository.upsertStanding(seasonId, teamA, BigDecimal(80), 5)

        first.id shouldBe second.id
        second.totalPoints.compareTo(BigDecimal(80)) shouldBe 0
        second.tournamentsPlayed shouldBe 5
    }

    test("getStandings returns standings for the season ordered by points desc") {
        repository.upsertStanding(seasonId, teamA, BigDecimal(50), 3)
        repository.upsertStanding(seasonId, teamB, BigDecimal(80), 4)

        val standings = repository.getStandings(seasonId)

        standings.map { it.teamId } shouldBe listOf(teamB, teamA)
    }
})
