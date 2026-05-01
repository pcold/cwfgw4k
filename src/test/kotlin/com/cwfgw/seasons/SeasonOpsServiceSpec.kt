package com.cwfgw.seasons

import com.cwfgw.golfers.GolferId
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.FantasyScoreId
import com.cwfgw.scoring.ScoringService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))

private fun tournament(
    id: TournamentId,
    name: String,
    startDate: String,
    status: TournamentStatus = TournamentStatus.Upcoming,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
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

private fun fantasyScore(
    teamHex: String,
    tournamentId: TournamentId,
): FantasyScore =
    FantasyScore(
        id = FantasyScoreId(UUID.randomUUID()),
        seasonId = SEASON_ID,
        teamId = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000$teamHex")),
        tournamentId = tournamentId,
        golferId = GolferId(UUID.randomUUID()),
        points = BigDecimal(18),
        position = 1,
        numTied = 1,
        basePayout = BigDecimal(18),
        ownershipPct = BigDecimal(100),
        payout = BigDecimal(18),
        multiplier = BigDecimal.ONE,
        calculatedAt = Instant.parse("2026-04-12T20:00:00Z"),
    )

private fun resultRow(
    tournamentId: TournamentId,
    golferId: GolferId,
    position: Int,
): TournamentResult =
    TournamentResult(
        id = TournamentResultId(UUID.randomUUID()),
        tournamentId = tournamentId,
        golferId = golferId,
        position = position,
        scoreToPar = -10,
        totalStrokes = 270,
        earnings = null,
        round1 = null,
        round2 = null,
        round3 = null,
        round4 = null,
        madeCut = true,
        pairKey = null,
    )

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

@Suppress("LongParameterList")
private class Fixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    initialScores: List<FantasyScore> = emptyList(),
) {
    val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
    val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
    val teamRepo = FakeTeamRepository()
    val scoringRepo = FakeScoringRepository(initialScores = initialScores)
    val service: SeasonOpsService

    init {
        if (seedSeason) {
            kotlinx.coroutines.runBlocking {
                with(noopTransactionContext) {

                    seasonRepo.create(
                        CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                    )
                }
            }
        }
        initialResults.forEach { result ->
            kotlinx.coroutines.runBlocking {
                with(noopTransactionContext) {
                    tournamentRepo.upsertResult(result.tournamentId, asUpsertRequest(result))
                }
            }
        }
        val seasonService = SeasonService(seasonRepo, FakeTransactor())
        val tournamentService = TournamentService(tournamentRepo, FakeTransactor())
        val teamService = TeamService(teamRepo, FakeTransactor())
        val scoringService =
            ScoringService(scoringRepo, seasonService, tournamentService, teamService, FakeTransactor())
        service = SeasonOpsService(seasonService, tournamentService, scoringService)
    }
}

class SeasonOpsServiceSpec : FunSpec({

    // ----- finalizeSeason -----

    test("finalizeSeason returns SeasonNotFound when the season id doesn't exist") {
        val fixture = Fixture(seedSeason = false)
        fixture.service.finalizeSeason(SEASON_ID) shouldBe Result.Err(SeasonOpsError.SeasonNotFound(SEASON_ID))
    }

    test("finalizeSeason returns SeasonHasNoTournaments when the season has zero tournaments") {
        val fixture = Fixture()
        fixture.service.finalizeSeason(SEASON_ID) shouldBe Result.Err(SeasonOpsError.SeasonHasNoTournaments)
    }

    test("finalizeSeason returns IncompleteTournaments listing every non-completed tournament") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Completed)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Upcoming)
        val fixture = Fixture(initialTournaments = listOf(sony, masters))

        val err =
            fixture.service.finalizeSeason(SEASON_ID)
                .shouldBeInstanceOf<Result.Err<SeasonOpsError>>()
                .error
                .shouldBeInstanceOf<SeasonOpsError.IncompleteTournaments>()
        err.incomplete.map { it.id } shouldContainExactly listOf(MASTERS_ID)
    }

    test("finalizeSeason flips season status to completed when every tournament is already completed") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Completed)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        val fixture = Fixture(initialTournaments = listOf(sony, masters))

        val finalized =
            fixture.service.finalizeSeason(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<Season>>()
                .value
        finalized.status shouldBe "completed"
    }

    // ----- cleanSeasonResults -----

    test("cleanSeasonResults returns SeasonNotFound when the season id doesn't exist") {
        val fixture = Fixture(seedSeason = false)
        fixture.service.cleanSeasonResults(SEASON_ID) shouldBe
            Result.Err(SeasonOpsError.SeasonNotFound(SEASON_ID))
    }

    test("cleanSeasonResults wipes scores + results + standings and reverts every tournament to upcoming") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Completed)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        val golferId = GolferId(UUID.randomUUID())
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialResults = listOf(resultRow(SONY_ID, golferId, position = 1)),
                initialScores = listOf(fantasyScore("c01", SONY_ID), fantasyScore("c01", MASTERS_ID)),
            )

        val result =
            fixture.service.cleanSeasonResults(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<CleanSeasonResult>>()
                .value
        result.scoresDeleted shouldBe 2
        result.resultsDeleted shouldBe 1
        result.tournamentsReset shouldBe 2

        with(noopTransactionContext) {
            fixture.tournamentRepo.getResults(SONY_ID).shouldBeEmpty()
            fixture.scoringRepo.getScores(SEASON_ID, SONY_ID).shouldBeEmpty()
            fixture.scoringRepo.getScores(SEASON_ID, MASTERS_ID).shouldBeEmpty()
            fixture.tournamentRepo.findById(SONY_ID)?.status shouldBe TournamentStatus.Upcoming
            fixture.tournamentRepo.findById(MASTERS_ID)?.status shouldBe TournamentStatus.Upcoming
        }
    }
})
