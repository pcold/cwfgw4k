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
import com.cwfgw.tournaments.ESPN_SCHEDULE_ZONE
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000c02"))
private val HERITAGE_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000c03"))
private val OPEN_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000c04"))

private fun tournament(
    id: TournamentId,
    name: String,
    startDate: LocalDate,
    status: TournamentStatus,
    week: String,
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
        week = week,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

/**
 * A finalized Sony, the Masters in play, the Heritage another week away but inside the look-ahead, and an Open
 * outside it. Dates hang off the real today because the service reads the clock itself.
 */
private class EligibilityFixture {
    val espn = FakeEspnClient()
    val service: WeeklyReportService

    init {
        val today = LocalDate.now(ESPN_SCHEDULE_ZONE)
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        with(noopTransactionContext) {
            seasonRepo.create(CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026))
        }
        val tournamentRepo =
            FakeTournamentRepository(
                initial =
                    listOf(
                        tournament(SONY_ID, "Sony Open", today.minusWeeks(8), TournamentStatus.Completed, "1"),
                        tournament(MASTERS_ID, "The Masters", today.minusDays(1), TournamentStatus.InProgress, "2"),
                        tournament(HERITAGE_ID, "RBC Heritage", today.plusDays(2), TournamentStatus.Upcoming, "3"),
                        tournament(OPEN_ID, "The Open", today.plusWeeks(2), TournamentStatus.Upcoming, "5"),
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

/** [requestLive] is judged by ESPN's call log: whether the overlay itself went looking for a preview. */
private class Endpoint(
    val name: String,
    val liveEligible: suspend (WeeklyReportService, TournamentId?) -> Boolean,
    val requestLive: suspend (WeeklyReportService, TournamentId?) -> Unit,
)

private val REPORT =
    Endpoint(
        name = "getReport",
        liveEligible = { service, id -> service.getReport(SEASON_ID, checkNotNull(id)).orFail().liveEligible },
        requestLive = { service, id -> service.getReport(SEASON_ID, checkNotNull(id), live = true).orFail() },
    )

private val RANKINGS =
    Endpoint(
        name = "getRankings",
        liveEligible = { service, id -> service.getRankings(SEASON_ID, id).orFail().liveEligible },
        requestLive = { service, id -> service.getRankings(SEASON_ID, id, live = true).orFail() },
    )

private val PLAYER_RANKINGS =
    Endpoint(
        name = "getPlayerRankings",
        liveEligible = { service, id -> service.getPlayerRankings(SEASON_ID, id).orFail().liveEligible },
        requestLive = { service, id -> service.getPlayerRankings(SEASON_ID, id, live = true).orFail() },
    )

private data class EligibilityCase(
    val endpoint: Endpoint,
    val label: String,
    val tournamentId: TournamentId?,
    val eligible: Boolean,
)

private val CASES =
    listOf(
        EligibilityCase(REPORT, "the completed Sony", SONY_ID, eligible = false),
        EligibilityCase(REPORT, "the Masters in play", MASTERS_ID, eligible = true),
        EligibilityCase(REPORT, "the Heritage, another week inside the look-ahead", HERITAGE_ID, eligible = true),
        EligibilityCase(REPORT, "the Open, another week beyond the look-ahead", OPEN_ID, eligible = false),
    ) +
        listOf(RANKINGS, PLAYER_RANKINGS).flatMap { endpoint ->
            listOf(
                EligibilityCase(endpoint, "no through", null, eligible = false),
                EligibilityCase(endpoint, "through the completed Sony", SONY_ID, eligible = false),
                EligibilityCase(endpoint, "through the Masters in play", MASTERS_ID, eligible = true),
                EligibilityCase(endpoint, "through the Heritage, another week", HERITAGE_ID, eligible = true),
                // The Masters before it is still in play, so the overlay folds that in.
                EligibilityCase(endpoint, "through the Open beyond the look-ahead", OPEN_ID, eligible = true),
            )
        }

class LiveOverlayEligibilitySpec : FunSpec({

    CASES.forEach { case ->
        test("${case.endpoint.name} for ${case.label} says liveEligible=${case.eligible}, and live=true agrees") {
            val fixture = EligibilityFixture()

            case.endpoint.liveEligible(fixture.service, case.tournamentId) shouldBe case.eligible
            fixture.espn.fetchCalls.shouldBeEmpty()

            case.endpoint.requestLive(fixture.service, case.tournamentId)
            if (case.eligible) fixture.espn.fetchCalls.shouldNotBeEmpty() else fixture.espn.fetchCalls.shouldBeEmpty()
        }
    }
})
