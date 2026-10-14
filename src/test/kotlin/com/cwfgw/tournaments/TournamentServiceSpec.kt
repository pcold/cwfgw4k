package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

class TournamentServiceSpec : FunSpec({

    fun service(initial: List<Tournament> = emptyList()): TournamentService =
        TournamentService(FakeTournamentRepository(initial = initial), FakeTransactor())

    test("list with no filters returns all tournaments") {
        val masters = tournament(name = "Masters")
        val open = tournament(name = "Open")

        val result = service(initial = listOf(masters, open)).list(seasonId = null, status = null)

        result.map { it.id } shouldContainExactlyInAnyOrder listOf(masters.id, open.id)
    }
})

@Suppress("LongParameterList")
private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    name: String = "The Masters",
    seasonId: SeasonId = SEASON_ID,
    startDate: LocalDate = LocalDate.parse("2026-04-09"),
    status: TournamentStatus = TournamentStatus.Upcoming,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = seasonId,
        startDate = startDate,
        endDate = startDate.plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = java.math.BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = java.time.Instant.parse("2026-04-01T00:00:00Z"),
    )
