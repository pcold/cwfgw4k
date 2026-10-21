package com.cwfgw.reports

import com.cwfgw.db.TransactionContext
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.leagues.LeagueId
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val PGA_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b03"))
private val US_OPEN_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b04"))
private val SCHEFFLER_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))
private val MCILROY_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d02"))

private fun completedTournament(
    id: TournamentId,
    name: String,
    startDate: String,
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
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

private fun finish(
    tournamentId: TournamentId,
    golferId: GolferId,
    position: Int,
): TournamentResult =
    TournamentResult(
        id = TournamentResultId(UUID.randomUUID()),
        tournamentId = tournamentId,
        golferId = golferId,
        position = position,
        scoreToPar = null,
        totalStrokes = null,
        earnings = null,
        round1 = null,
        round2 = null,
        round3 = null,
        round4 = null,
        madeCut = true,
        pairKey = null,
    )

/** Records the tournament ids each season-wide score read asked for; `null` is a read of every tournament. */
private class RecordingScoringRepository(
    private val delegate: ScoringRepository = FakeScoringRepository(),
) : ScoringRepository by delegate {
    val seasonReads = mutableListOf<Set<TournamentId>?>()

    context(ctx: TransactionContext)
    override fun getScoresBySeason(
        seasonId: SeasonId,
        tournamentIds: Collection<TournamentId>?,
    ): List<FantasyScore> {
        seasonReads += tournamentIds?.toSet()
        return delegate.getScoresBySeason(seasonId, tournamentIds)
    }
}

private class ReadScopeFixture(
    results: List<TournamentResult> = emptyList(),
    unfinished: List<Tournament> = emptyList(),
) {
    val scoring = RecordingScoringRepository()
    val service: WeeklyReportService

    init {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        with(noopTransactionContext) {
            seasonRepo.create(CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026))
        }
        val tournamentRepo =
            FakeTournamentRepository(
                initial =
                    listOf(
                        completedTournament(SONY_ID, "Sony Open", "2026-01-15"),
                        completedTournament(MASTERS_ID, "The Masters", "2026-04-09"),
                        completedTournament(PGA_ID, "PGA Championship", "2026-05-14"),
                    ) + unfinished,
                initialResults = results,
            )
        val teamRepo = FakeTeamRepository()
        val golferRepo =
            FakeGolferRepository(
                initial = listOf(golfer(SCHEFFLER_ID, "Scottie", "Scheffler"), golfer(MCILROY_ID, "Rory", "McIlroy")),
            )
        service =
            WeeklyReportService(
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                golferRepository = golferRepo,
                scoringRepository = scoring,
                liveOverlayService = LiveOverlayService(testEspnService(client = FakeEspnClient())),
                tx = FakeTransactor(),
            )
    }

    private fun golfer(
        id: GolferId,
        firstName: String,
        lastName: String,
    ): Golfer =
        Golfer(
            id = id,
            pgaPlayerId = null,
            firstName = firstName,
            lastName = lastName,
            country = null,
            worldRanking = null,
            updatedAt = Instant.EPOCH,
        )
}

class WeeklyReportReadScopeSpec : FunSpec({

    test("getReport reads season scores only for the completed tournaments up to the one reported") {
        val fixture = ReadScopeFixture()

        fixture.service.getReport(SEASON_ID, MASTERS_ID).orFail()

        fixture.scoring.seasonReads shouldContainExactly listOf(setOf(SONY_ID, MASTERS_ID))
    }

    test("getSeasonReport leaves out a tournament still in play, though it already has a top-10 result") {
        val usOpen =
            completedTournament(US_OPEN_ID, "U.S. Open", "2026-06-18").copy(status = TournamentStatus.InProgress)
        val fixture =
            ReadScopeFixture(
                results =
                    listOf(
                        finish(SONY_ID, SCHEFFLER_ID, position = 1),
                        finish(US_OPEN_ID, MCILROY_ID, position = 2),
                    ),
                unfinished = listOf(usOpen),
            )

        val report = fixture.service.getSeasonReport(SEASON_ID).orFail()

        report.undraftedTopTens.map { it.name } shouldContainExactly listOf("S. Scheffler")
        fixture.scoring.seasonReads shouldContainExactly listOf(setOf(SONY_ID, MASTERS_ID, PGA_ID))
    }

    test("getGolferHistory splits a two-way T3 across both tied golfers, though only one is asked about") {
        val fixture =
            ReadScopeFixture(
                results =
                    listOf(
                        finish(SONY_ID, SCHEFFLER_ID, position = 3),
                        finish(SONY_ID, MCILROY_ID, position = 3),
                    ),
            )

        val history = fixture.service.getGolferHistory(SEASON_ID, SCHEFFLER_ID).orFail()

        // Default payouts: 3rd $10 and 4th $8, averaged; an untied 3rd would pay the full $10.
        history.results.single().earnings.compareTo(BigDecimal(9)) shouldBe 0
    }
})
