package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))
private val TODAY = LocalDate.parse("2026-05-10")

class TournamentPredicatesSpec : FunSpec({

    test("a completed tournament whose start is in the past is not a live candidate") {
        val event = tournament(startDate = "2026-05-03", status = TournamentStatus.Completed)
        event.isLiveOverlayCandidate(TODAY) shouldBe false
    }

    test("a completed tournament whose start is today is not a live candidate") {
        val event = tournament(startDate = "2026-05-10", status = TournamentStatus.Completed)
        event.isLiveOverlayCandidate(TODAY) shouldBe false
    }

    test("an in-progress tournament whose start is in the past is a live candidate") {
        val event = tournament(startDate = "2026-05-08", status = TournamentStatus.InProgress)
        event.isLiveOverlayCandidate(TODAY) shouldBe true
    }

    test("an upcoming tournament whose start is today is a live candidate") {
        // The day a tournament opens, ESPN's scoreboard begins reporting before the
        // operator manually flips status from Upcoming → InProgress. Letting today's
        // Upcoming events through is the whole reason the predicate is "started" and
        // not "InProgress."
        val event = tournament(startDate = "2026-05-10", status = TournamentStatus.Upcoming)
        event.isLiveOverlayCandidate(TODAY) shouldBe true
    }

    test("an upcoming tournament whose start is within the look-ahead window is a live candidate") {
        // 3 days out — Monday → Thursday start matches the canonical PGA tee-time
        // publish window. ESPN's scoreboard starts carrying pairings + field
        // commitments somewhere between Mon and Wed, and we want that data visible.
        val event = tournament(startDate = "2026-05-13", status = TournamentStatus.Upcoming)
        event.isLiveOverlayCandidate(TODAY) shouldBe true
    }

    test("an upcoming tournament whose start is one day past the look-ahead window is NOT a live candidate") {
        // 4 days out — just beyond the window. Excluded because there's typically
        // no scoreboard payload yet and we don't want to pay the fan-out cost.
        val event = tournament(startDate = "2026-05-14", status = TournamentStatus.Upcoming)
        event.isLiveOverlayCandidate(TODAY) shouldBe false
    }

    test("an in-progress tournament whose start is well beyond the look-ahead is NOT a live candidate") {
        // Defensive: an InProgress row with a far-future start date shouldn't exist,
        // but if it does (operator error, data import bug) we still skip the ESPN
        // fetch — there is no scoreboard for a date that hasn't happened.
        val event = tournament(startDate = "2026-05-20", status = TournamentStatus.InProgress)
        event.isLiveOverlayCandidate(TODAY) shouldBe false
    }
})

@Suppress("LongParameterList")
private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    startDate: String,
    status: TournamentStatus,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = "Test Event",
        seasonId = SEASON_ID,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
    )
