package com.cwfgw.tournaments

import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.FantasyScoreId
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
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
    pgaTournamentId: String? = "espn-${id.value}",
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = pgaTournamentId,
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

@Suppress("LongParameterList")
private fun fantasyScore(
    teamHex: String,
    tournamentId: TournamentId,
    points: BigDecimal = BigDecimal(18),
): FantasyScore =
    FantasyScore(
        id = FantasyScoreId(UUID.randomUUID()),
        seasonId = SEASON_ID,
        teamId = com.cwfgw.teams.TeamId(UUID.fromString("00000000-0000-0000-0000-000000000$teamHex")),
        tournamentId = tournamentId,
        golferId = GolferId(UUID.randomUUID()),
        points = points,
        position = 1,
        numTied = 1,
        basePayout = points,
        ownershipPct = BigDecimal(100),
        payout = points,
        multiplier = BigDecimal.ONE,
        calculatedAt = Instant.parse("2026-04-12T20:00:00Z"),
    )

@Suppress("LongParameterList")
private class Fixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    initialGolfers: List<Golfer> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    initialScores: List<FantasyScore> = emptyList(),
    espnByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    espnUpstreamError: EspnUpstreamException? = null,
) {
    val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
    val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
    val teamRepo = FakeTeamRepository()
    val golferRepo = FakeGolferRepository(initial = initialGolfers)
    val scoringRepo = FakeScoringRepository(initialScores = initialScores)
    val service: TournamentOpsService

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
        val golferService = GolferService(golferRepo, FakeTransactor())
        val espnClient = FakeEspnClient(tournamentsByDate = espnByDate, upstreamError = espnUpstreamError)
        val espnService =
            EspnService(
                espnClient,
                tournamentService,
                golferService,
                teamService,
                seasonService,
                TournamentLinkService(
                    FakeTournamentLinkRepository(),
                    tournamentRepo,
                    golferRepo,
                    FakeTransactor(),
                ),
            )
        val scoringService =
            ScoringService(scoringRepo, seasonService, tournamentService, teamService, FakeTransactor())
        service = TournamentOpsService(tournamentService, scoringService, espnService)
    }
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

class TournamentOpsServiceSpec : FunSpec({

    // ----- finalizeTournament -----

    test("finalizeTournament returns TournamentNotFound when the id doesn't exist") {
        val fixture = Fixture()
        fixture.service.finalizeTournament(MASTERS_ID) shouldBe
            Result.Err(TournamentOpsError.TournamentNotFound(MASTERS_ID))
    }

    test("finalizeTournament rejects with OutOfOrder when an earlier tournament is still upcoming") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Upcoming)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Upcoming)
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                espnByDate =
                    mapOf(
                        LocalDate.parse("2026-04-09") to
                            listOf(emptyEspnEvent("espn-${MASTERS_ID.value}", "Masters")),
                    ),
            )

        val err =
            fixture.service.finalizeTournament(MASTERS_ID)
                .shouldBeInstanceOf<Result.Err<TournamentOpsError>>()
                .error
                .shouldBeInstanceOf<TournamentOpsError.OutOfOrder>()
        err.action shouldBe TournamentOpsError.Action.Finalize
        err.blocking.map { it.id } shouldContainExactly listOf(SONY_ID)
    }

    test("finalizeTournament happy path runs ESPN import + scoring and flips status to completed") {
        val masters =
            tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Upcoming)
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                espnByDate =
                    mapOf(
                        LocalDate.parse("2026-04-09") to
                            listOf(emptyEspnEvent("espn-${MASTERS_ID.value}", "Masters")),
                    ),
            )

        val finalized =
            fixture.service.finalizeTournament(MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<Tournament>>()
                .value
        finalized.status shouldBe TournamentStatus.Completed
    }

    test("finalizeTournament surfaces UpstreamUnavailable when ESPN is down") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                espnUpstreamError = EspnUpstreamException(status = 503, message = "Service Unavailable"),
            )

        fixture.service.finalizeTournament(MASTERS_ID) shouldBe
            Result.Err(TournamentOpsError.UpstreamUnavailable(503))
    }

    // ----- resetTournament -----

    test("resetTournament returns TournamentNotFound when the id doesn't exist") {
        val fixture = Fixture()
        fixture.service.resetTournament(MASTERS_ID) shouldBe
            Result.Err(TournamentOpsError.TournamentNotFound(MASTERS_ID))
    }

    test("resetTournament rejects with OutOfOrder when a later tournament is already completed") {
        val sony = tournament(SONY_ID, "Sony Open", "2026-01-15", status = TournamentStatus.Completed)
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        val fixture = Fixture(initialTournaments = listOf(sony, masters))

        val err =
            fixture.service.resetTournament(SONY_ID)
                .shouldBeInstanceOf<Result.Err<TournamentOpsError>>()
                .error
                .shouldBeInstanceOf<TournamentOpsError.OutOfOrder>()
        err.action shouldBe TournamentOpsError.Action.Reset
        err.blocking.map { it.id } shouldContainExactly listOf(MASTERS_ID)
    }

    test("resetTournament wipes scores + results and reverts status to upcoming") {
        val masters = tournament(MASTERS_ID, "The Masters", "2026-04-09", status = TournamentStatus.Completed)
        val golferId = GolferId(UUID.randomUUID())
        val result =
            TournamentResult(
                id = TournamentResultId(UUID.randomUUID()),
                tournamentId = MASTERS_ID,
                golferId = golferId,
                position = 1,
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
        val score = fantasyScore("c01", MASTERS_ID)
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialResults = listOf(result),
                initialScores = listOf(score),
            )

        val reset =
            fixture.service.resetTournament(MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<Tournament>>()
                .value

        reset.status shouldBe TournamentStatus.Upcoming
        with(noopTransactionContext) {
            fixture.tournamentRepo.getResults(MASTERS_ID).shouldBeEmpty()
            fixture.scoringRepo.getScores(SEASON_ID, MASTERS_ID).shouldBeEmpty()
        }
    }
})
