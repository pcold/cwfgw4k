package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))
private val OTHER_SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa2"))
private val MARCH_12 = LocalDate.parse("2026-03-12")
private val APRIL_9 = LocalDate.parse("2026-04-09")
private val APRIL_16 = LocalDate.parse("2026-04-16")
private val APRIL_1 = Instant.parse("2026-04-01T00:00:00Z")
private val APRIL_2 = Instant.parse("2026-04-02T00:00:00Z")

class TournamentServiceSpec : FunSpec({

    fun service(initial: List<Tournament> = emptyList()): TournamentService =
        TournamentService(FakeTournamentRepository(initial = initial), FakeTransactor())

    test("list with no filters returns all tournaments") {
        val masters = tournament(name = "Masters")
        val open = tournament(name = "Open")

        val result = service(initial = listOf(masters, open)).list(TournamentFilter())

        result.map { it.id } shouldContainExactlyInAnyOrder listOf(masters.id, open.id)
    }

    test("current is the earliest unfinalized tournament, past a finalized one and before a later one in progress") {
        val players = tournament(name = "Players", startDate = MARCH_12, status = TournamentStatus.Completed)
        val masters = tournament(name = "Masters", startDate = APRIL_9)
        val heritage = tournament(name = "Heritage", startDate = APRIL_16, status = TournamentStatus.InProgress)

        service(initial = listOf(heritage, masters, players)).current(SEASON_ID) shouldBe
            CurrentTournament.Unfinalized(masters.id)
    }

    test("current falls back to the season's latest tournament once every one is finalized") {
        val players = tournament(name = "Players", startDate = MARCH_12, status = TournamentStatus.Completed)
        val heritage = tournament(name = "Heritage", startDate = APRIL_16, status = TournamentStatus.Completed)
        val masters = tournament(name = "Masters", startDate = APRIL_9, status = TournamentStatus.Completed)

        service(initial = listOf(players, heritage, masters)).current(SEASON_ID) shouldBe
            CurrentTournament.SeasonFinalized(heritage.id)
    }

    test("current answers no tournaments for an empty season, ignoring another season's") {
        val elsewhere = tournament(name = "Elsewhere", seasonId = OTHER_SEASON_ID)

        service(initial = listOf(elsewhere)).current(SEASON_ID) shouldBe CurrentTournament.NoTournaments
    }

    test("current names a same-date 8a before an 8b created earlier") {
        val sameWeek = LocalDate.parse("2026-07-16")
        val oppositeField =
            tournament(name = "Opposite Field", startDate = sameWeek, week = "8b", createdAt = APRIL_1)
        val mainEvent = tournament(name = "Main Event", startDate = sameWeek, week = "8a", createdAt = APRIL_2)

        service(initial = listOf(oppositeField, mainEvent)).current(SEASON_ID) shouldBe
            CurrentTournament.Unfinalized(mainEvent.id)
    }

    test("current breaks a start-date tie by creation, then by id") {
        val sameWeek = LocalDate.parse("2026-07-16")
        val laterCreated =
            tournament(name = "Opposite Field", startDate = sameWeek, createdAt = Instant.parse("2026-04-02T00:00:00Z"))
        val lowId = tournament(id = tournamentId("00000000-0000-0000-0000-000000000001"), startDate = sameWeek)
        val highId = tournament(id = tournamentId("00000000-0000-0000-0000-000000000002"), startDate = sameWeek)

        service(initial = listOf(laterCreated, highId, lowId)).current(SEASON_ID) shouldBe
            CurrentTournament.Unfinalized(lowId.id)
        service(initial = listOf(laterCreated, highId)).current(SEASON_ID) shouldBe
            CurrentTournament.Unfinalized(highId.id)
    }
})

private fun tournamentId(value: String): TournamentId = TournamentId(UUID.fromString(value))

private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    name: String = "The Masters",
    seasonId: SeasonId = SEASON_ID,
    startDate: LocalDate = LocalDate.parse("2026-04-09"),
    status: TournamentStatus = TournamentStatus.Upcoming,
    week: String? = null,
    createdAt: Instant = Instant.parse("2026-04-01T00:00:00Z"),
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
        payoutMultiplier = BigDecimal("1.0000"),
        week = week,
        isTeamEvent = false,
        createdAt = createdAt,
    )
