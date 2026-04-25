package com.cwfgw.tournaments

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

class TournamentServiceSpec : FunSpec({

    fun service(
        initial: List<Tournament> = emptyList(),
        initialResults: List<TournamentResult> = emptyList(),
    ): TournamentService =
        TournamentService(
            FakeTournamentRepository(initial = initial, initialResults = initialResults),
        )

    test("list with no filters returns all tournaments") {
        val masters = tournament(name = "Masters")
        val open = tournament(name = "Open")

        val result = service(initial = listOf(masters, open)).list(seasonId = null, status = null)

        result.map { it.id } shouldContainExactlyInAnyOrder listOf(masters.id, open.id)
    }

    test("get returns null for an unknown id") {
        service().get(TournamentId(UUID.randomUUID())).shouldBeNull()
    }

    test("importResults upserts every entry and returns the new results in order") {
        val t = tournament()
        val svc = service(initial = listOf(t))

        val result =
            svc.importResults(
                t.id,
                listOf(
                    CreateTournamentResultRequest(golferId = GolferId(UUID.randomUUID()), position = 1),
                    CreateTournamentResultRequest(golferId = GolferId(UUID.randomUUID()), position = 2),
                ),
            )

        result.shouldNotBeNull()
        result.map { it.position } shouldContainExactly listOf(1, 2)
    }
})

private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    name: String = "The Masters",
    seasonId: SeasonId = SEASON_ID,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = seasonId,
        startDate = LocalDate.parse("2026-04-09"),
        endDate = LocalDate.parse("2026-04-12"),
        courseName = null,
        status = TournamentStatus.Upcoming,
        purseAmount = null,
        payoutMultiplier = java.math.BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = java.time.Instant.parse("2026-04-01T00:00:00Z"),
    )
