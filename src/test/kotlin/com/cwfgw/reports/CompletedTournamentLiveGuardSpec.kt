package com.cwfgw.reports

import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.leagues.LeagueId
import com.cwfgw.scoring.FakeScoringRepository
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
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val SONY_START: LocalDate = LocalDate.parse("2026-01-15")
private val MASTERS_START: LocalDate = LocalDate.parse("2026-04-09")

private fun tournament(
    id: TournamentId,
    name: String,
    startDate: LocalDate,
    status: TournamentStatus,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = SEASON_ID,
        startDate = startDate,
        endDate = startDate.plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

/**
 * A finalized Sony Open ahead of a Masters still in play. The Masters is what keeps each spec from
 * passing on a live flag that was ignored: ESPN has to be asked for it, and for nothing else.
 */
private class GuardFixture {
    val espn = FakeEspnClient()
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
                        tournament(SONY_ID, "Sony Open", SONY_START, TournamentStatus.Completed),
                        tournament(MASTERS_ID, "The Masters", MASTERS_START, TournamentStatus.InProgress),
                    ),
            )
        val teamRepo = FakeTeamRepository()
        val golferRepo = FakeGolferRepository()
        val espnService =
            testEspnService(
                client = espn,
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
            )
        service =
            WeeklyReportService(
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                golferRepository = golferRepo,
                scoringRepository = FakeScoringRepository(),
                liveOverlayService = LiveOverlayService(espnService),
                tx = FakeTransactor(),
            )
    }
}

class CompletedTournamentLiveGuardSpec : FunSpec({

    test("getSeasonReport with live=true asks ESPN for the tournament in play and never the completed one") {
        val fixture = GuardFixture()

        fixture.service.getSeasonReport(SEASON_ID, live = true).orFail()

        fixture.espn.fetchCalls.toSet() shouldBe setOf(MASTERS_START)
    }

    test("getRankings with live=true asks ESPN for the tournament in play and never the completed one") {
        val fixture = GuardFixture()

        fixture.service.getRankings(SEASON_ID, live = true).orFail()

        fixture.espn.fetchCalls.toSet() shouldBe setOf(MASTERS_START)
    }

    test("getPlayerRankings with live=true asks ESPN for the tournament in play and never the completed one") {
        val fixture = GuardFixture()

        fixture.service.getPlayerRankings(SEASON_ID, live = true).orFail()

        fixture.espn.fetchCalls.toSet() shouldBe setOf(MASTERS_START)
    }

    test("getReport with live=true on the tournament in play never asks ESPN for a completed earlier one") {
        val fixture = GuardFixture()

        fixture.service.getReport(SEASON_ID, MASTERS_ID, live = true).orFail()

        fixture.espn.fetchCalls.toSet() shouldBe setOf(MASTERS_START)
    }
})
