package com.cwfgw.tournaments

import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.result.Result
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.testEspnService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val OTHER_SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000bbb"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val HERITAGE_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b04"))
private val PGA_CHAMPIONSHIP_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b03"))

private fun tournament(
    id: TournamentId,
    startDate: String,
    status: TournamentStatus,
    seasonId: SeasonId = SEASON_ID,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = "Tournament ${id.value}",
        seasonId = seasonId,
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

private fun opsService(vararg tournaments: Tournament): TournamentOpsService {
    val tournamentRepo = FakeTournamentRepository(initial = tournaments.toList())
    val seasonRepo = FakeSeasonRepository()
    val teamRepo = FakeTeamRepository()
    val scoringRepo = FakeScoringRepository()
    val transactor = FakeTransactor()
    return TournamentOpsService(
        tournamentRepository = tournamentRepo,
        scoringService = ScoringService(scoringRepo, seasonRepo, tournamentRepo, teamRepo),
        scoringRepository = scoringRepo,
        espnService =
            testEspnService(client = FakeEspnClient(), tournamentRepository = tournamentRepo, tx = transactor),
        tx = transactor,
    )
}

class TournamentOpsResettableSpec : FunSpec({

    test("listResettable lists only the completed tournaments with none completed after them") {
        val service =
            opsService(
                tournament(SONY_ID, "2026-01-15", TournamentStatus.Completed),
                tournament(MASTERS_ID, "2026-04-09", TournamentStatus.Completed),
                tournament(PGA_CHAMPIONSHIP_ID, "2026-05-14", TournamentStatus.Upcoming),
            )

        service.listResettable(SEASON_ID).map(Tournament::id) shouldContainExactly listOf(MASTERS_ID)
    }

    test("listResettable lists every completed tournament sharing the latest start date") {
        val service =
            opsService(
                tournament(SONY_ID, "2026-01-15", TournamentStatus.Completed),
                tournament(MASTERS_ID, "2026-04-09", TournamentStatus.Completed),
                tournament(HERITAGE_ID, "2026-04-09", TournamentStatus.Completed),
            )

        service.listResettable(SEASON_ID).map(Tournament::id) shouldContainExactly listOf(MASTERS_ID, HERITAGE_ID)
    }

    test("listResettable ignores another season's completed tournaments, listing and blocking alike") {
        val service =
            opsService(
                tournament(MASTERS_ID, "2026-04-09", TournamentStatus.Completed),
                tournament(PGA_CHAMPIONSHIP_ID, "2026-05-14", TournamentStatus.Completed, seasonId = OTHER_SEASON_ID),
            )

        service.listResettable(SEASON_ID).map(Tournament::id) shouldContainExactly listOf(MASTERS_ID)
    }

    test("listResettable lists nothing for a season with no completed tournament") {
        val service = opsService(tournament(MASTERS_ID, "2026-04-09", TournamentStatus.InProgress))

        service.listResettable(SEASON_ID).shouldBeEmpty()
    }

    test("resetTournament accepts each completed tournament listResettable lists and refuses each it omits") {
        val season =
            arrayOf(
                tournament(SONY_ID, "2026-01-15", TournamentStatus.Completed),
                tournament(MASTERS_ID, "2026-04-09", TournamentStatus.Completed),
                tournament(HERITAGE_ID, "2026-04-09", TournamentStatus.Completed),
            )
        val listed = opsService(*season).listResettable(SEASON_ID).map(Tournament::id).toSet()

        season.forEach { completed ->
            val reset = opsService(*season).resetTournament(completed.id)
            if (completed.id in listed) {
                reset.shouldBeInstanceOf<Result.Ok<Tournament>>()
            } else {
                reset.shouldBeInstanceOf<Result.Err<TournamentOpsError>>()
                    .error
                    .shouldBeInstanceOf<TournamentOpsError.OutOfOrder>()
            }
        }
    }
})
