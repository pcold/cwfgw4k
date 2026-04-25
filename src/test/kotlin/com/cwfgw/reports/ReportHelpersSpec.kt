package com.cwfgw.reports

import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))

private fun tournament(
    idHex: String,
    name: String,
    startDate: String,
    week: String? = null,
): Tournament =
    Tournament(
        id = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000$idHex")),
        pgaTournamentId = null,
        name = name,
        seasonId = SEASON_ID,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
        courseName = null,
        status = "completed",
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = week,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun teamColumn(
    teamHex: String,
    teamName: String,
    totalCash: BigDecimal,
): ReportTeamColumn =
    ReportTeamColumn(
        teamId = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000$teamHex")),
        teamName = teamName,
        ownerName = teamName,
        cells = emptyList(),
        topTenEarnings = BigDecimal.ZERO,
        weeklyTotal = BigDecimal.ZERO,
        previous = BigDecimal.ZERO,
        subtotal = BigDecimal.ZERO,
        topTenCount = 0,
        topTenMoney = BigDecimal.ZERO,
        sideBets = BigDecimal.ZERO,
        totalCash = totalCash,
    )

private fun sideBetEntry(
    teamHex: String,
    cumulative: BigDecimal,
): ReportSideBetTeamEntry =
    ReportSideBetTeamEntry(
        teamId = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000$teamHex")),
        golferName = "Golfer $teamHex",
        cumulativeEarnings = cumulative,
        payout = BigDecimal.ZERO,
    )

class ReportHelpersSpec : FunSpec({

    // ----- formatScoreToPar -----

    test("formatScoreToPar renders even par as 'E', over par with a leading +, under par as a signed integer") {
        formatScoreToPar(0) shouldBe "E"
        formatScoreToPar(5) shouldBe "+5"
        formatScoreToPar(-3) shouldBe "-3"
    }

    test("formatScoreToPar always parses back to the same integer (round-trip property)") {
        checkAll(Arb.int(-30..30)) { score ->
            val rendered = formatScoreToPar(score)
            val recovered = if (rendered == "E") 0 else rendered.replace("+", "").toInt()
            recovered shouldBe score
        }
    }

    // ----- tournament ordering -----

    test("tournamentOrdering sorts primarily by start date") {
        val later = tournament("01", "Sony Open", "2026-01-22")
        val earlier = tournament("02", "Tournament of Champions", "2026-01-08")
        listOf(later, earlier).sortedWith(tournamentOrdering).map { it.name } shouldContainExactly
            listOf("Tournament of Champions", "Sony Open")
    }

    test("tournamentOrdering breaks ties by week so same-date multi-events (8A / 8B) have a stable order") {
        val eventB = tournament("01", "Zurich Classic", "2026-04-09", week = "8B")
        val eventA = tournament("02", "RBC Heritage", "2026-04-09", week = "8A")
        listOf(eventB, eventA).sortedWith(tournamentOrdering).map { it.week } shouldContainExactly
            listOf("8A", "8B")
    }

    test("isBefore is strict and isOnOrBefore is reflexive on the same tournament") {
        val masters = tournament("01", "The Masters", "2026-04-09")
        val sony = tournament("02", "Sony Open", "2026-01-22")

        isBefore(sony, masters) shouldBe true
        isBefore(masters, sony) shouldBe false
        isBefore(masters, masters) shouldBe false
        isOnOrBefore(masters, masters) shouldBe true
        isOnOrBefore(sony, masters) shouldBe true
    }

    // ----- buildStandingsOrder -----

    test("buildStandingsOrder ranks teams by totalCash descending and assigns sequential 1-based ranks") {
        val standings =
            buildStandingsOrder(
                listOf(
                    teamColumn("01", "Last Place", BigDecimal("100")),
                    teamColumn("02", "First Place", BigDecimal("500")),
                    teamColumn("03", "Middle", BigDecimal("250")),
                ),
            )

        standings.map { it.rank } shouldContainExactly listOf(1, 2, 3)
        standings.map { it.teamName } shouldContainExactly listOf("First Place", "Middle", "Last Place")
    }

    test("buildStandingsOrder gives tied teams sequential ranks (no joint-rank logic)") {
        val standings =
            buildStandingsOrder(
                listOf(
                    teamColumn("01", "Alpha", BigDecimal("250")),
                    teamColumn("02", "Beta", BigDecimal("250")),
                ),
            )

        standings.map { it.rank } shouldContainExactly listOf(1, 2)
    }

    test("buildStandingsOrder on an empty input returns an empty standings list") {
        buildStandingsOrder(emptyList()).shouldBeEmpty()
    }

    // ----- recomputeSideBetPayouts -----

    test("recomputeSideBetPayouts zeroes every payout when nobody has earned anything yet") {
        val entries =
            listOf(
                sideBetEntry("01", BigDecimal.ZERO),
                sideBetEntry("02", BigDecimal.ZERO),
                sideBetEntry("03", BigDecimal.ZERO),
            )

        recomputeSideBetPayouts(entries, numTeams = 3, sideBetPerTeam = BigDecimal("5"))
            .map { it.payout } shouldContainExactly listOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }

    test("recomputeSideBetPayouts gives the single leader the pool and charges every other team sideBetPerTeam") {
        val entries =
            listOf(
                sideBetEntry("01", BigDecimal("100")),
                sideBetEntry("02", BigDecimal("250")),
                sideBetEntry("03", BigDecimal("75")),
            )

        val result = recomputeSideBetPayouts(entries, numTeams = 3, sideBetPerTeam = BigDecimal("5"))
        result.single { it.teamId == entries[0].teamId }.payout shouldBe BigDecimal("-5")
        result.single { it.teamId == entries[1].teamId }.payout shouldBe BigDecimal("10")
        result.single { it.teamId == entries[2].teamId }.payout shouldBe BigDecimal("-5")
    }

    test("recomputeSideBetPayouts splits the pool when multiple teams tie for the lead") {
        val entries =
            listOf(
                sideBetEntry("01", BigDecimal("250")),
                sideBetEntry("02", BigDecimal("250")),
                sideBetEntry("03", BigDecimal("75")),
                sideBetEntry("04", BigDecimal("100")),
            )

        val result = recomputeSideBetPayouts(entries, numTeams = 4, sideBetPerTeam = BigDecimal("6"))
        // Two losers pay 6 each = 12 pool; split 2 ways = 6 each to the leaders.
        result.single { it.teamId == entries[0].teamId }.payout shouldBe BigDecimal("6")
        result.single { it.teamId == entries[1].teamId }.payout shouldBe BigDecimal("6")
        result.single { it.teamId == entries[2].teamId }.payout shouldBe BigDecimal("-6")
        result.single { it.teamId == entries[3].teamId }.payout shouldBe BigDecimal("-6")
    }

    // ----- filterThroughTournament -----

    test("filterThroughTournament returns the input unchanged when the cutoff is null") {
        val sony = tournament("01", "Sony Open", "2026-01-22")
        val masters = tournament("02", "The Masters", "2026-04-09")

        filterThroughTournament(listOf(sony, masters), through = null) shouldContainExactly listOf(sony, masters)
    }

    test("filterThroughTournament includes the cutoff itself and drops anything strictly later") {
        val sony = tournament("01", "Sony Open", "2026-01-22")
        val masters = tournament("02", "The Masters", "2026-04-09")
        val open = tournament("03", "U.S. Open", "2026-06-18")

        filterThroughTournament(listOf(sony, masters, open), through = masters) shouldContainExactly
            listOf(sony, masters)
    }
})
