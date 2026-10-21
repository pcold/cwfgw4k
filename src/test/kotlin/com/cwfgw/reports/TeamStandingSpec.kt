package com.cwfgw.reports

import com.cwfgw.teams.TeamId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.util.UUID

private fun ranking(
    index: Int,
    totalCash: String,
): TeamRanking =
    TeamRanking(
        teamId = TeamId(UUID(0L, index.toLong())),
        teamName = "TEAM $index",
        subtotal = BigDecimal(totalCash),
        sideBets = BigDecimal.ZERO,
        totalCash = BigDecimal(totalCash),
        series = emptyList(),
    )

private fun rankingsOf(vararg totals: String): List<TeamRanking> =
    totals.mapIndexed { index, total -> ranking(index = index + 1, totalCash = total) }

private fun teamId(index: Int): TeamId = TeamId(UUID(0L, index.toLong()))

private fun gap(
    position: Int,
    amount: String,
    tied: Boolean = false,
): StandingGap = StandingGap(position = position, tied = tied, amount = BigDecimal(amount))

private fun positionOf(standing: TeamStanding): Int =
    when (standing) {
        is TeamStanding.Leader -> 1
        is TeamStanding.Chaser -> standing.position
    }

class TeamStandingSpec : FunSpec({

    test("distinct totals each take their own position, behind the team directly above") {
        val standings = TeamStanding.over(rankingsOf("1082", "639", "407"))

        standings shouldContainExactly
            listOf(
                TeamStanding.Leader(teamId = teamId(1), tied = false, aheadOf = gap(position = 2, amount = "443")),
                TeamStanding.Chaser(
                    teamId = teamId(2),
                    tied = false,
                    position = 2,
                    behind = gap(position = 1, amount = "443"),
                    behindLeader = BigDecimal("443"),
                ),
                TeamStanding.Chaser(
                    teamId = teamId(3),
                    tied = false,
                    position = 3,
                    behind = gap(position = 2, amount = "232"),
                    behindLeader = BigDecimal("675"),
                ),
            )
    }

    test("a tie in the middle shares a position and the next total skips past it (1, 2, 2, 4)") {
        val standings = TeamStanding.over(rankingsOf("100", "60", "60", "25"))

        standings shouldContainExactly
            listOf(
                TeamStanding.Leader(
                    teamId = teamId(1),
                    tied = false,
                    aheadOf = gap(position = 2, amount = "40", tied = true),
                ),
                TeamStanding.Chaser(
                    teamId = teamId(2),
                    tied = true,
                    position = 2,
                    behind = gap(position = 1, amount = "40"),
                    behindLeader = BigDecimal("40"),
                ),
                TeamStanding.Chaser(
                    teamId = teamId(3),
                    tied = true,
                    position = 2,
                    behind = gap(position = 1, amount = "40"),
                    behindLeader = BigDecimal("40"),
                ),
                TeamStanding.Chaser(
                    teamId = teamId(4),
                    tied = false,
                    position = 4,
                    behind = gap(position = 2, amount = "35", tied = true),
                    behindLeader = BigDecimal("75"),
                ),
            )
    }

    test("teams tied for first are both leaders, ahead of the total that ranks third") {
        val standings = TeamStanding.over(rankingsOf("90", "90", "50"))

        standings shouldContainExactly
            listOf(
                TeamStanding.Leader(teamId = teamId(1), tied = true, aheadOf = gap(position = 3, amount = "40")),
                TeamStanding.Leader(teamId = teamId(2), tied = true, aheadOf = gap(position = 3, amount = "40")),
                TeamStanding.Chaser(
                    teamId = teamId(3),
                    tied = false,
                    position = 3,
                    behind = gap(position = 1, amount = "40", tied = true),
                    behindLeader = BigDecimal("40"),
                ),
            )
    }

    test("all teams tied are all tied leaders with nobody to be ahead of") {
        val standings = TeamStanding.over(rankingsOf("0", "0", "0"))

        standings shouldContainExactly
            (1..3).map { index -> TeamStanding.Leader(teamId = teamId(index), tied = true, aheadOf = null) }
    }

    test("equal totals written at different scales are one tie") {
        val standings = TeamStanding.over(rankingsOf("12.5", "12.50"))

        standings.map(TeamStanding::tied) shouldContainExactly listOf(true, true)
    }

    test("a single team leads alone with nobody to be ahead of") {
        TeamStanding.over(rankingsOf("-179")) shouldContainExactly
            listOf(TeamStanding.Leader(teamId = teamId(1), tied = false, aheadOf = null))
    }

    test("negative totals measure each gap as a positive distance") {
        val standings = TeamStanding.over(rankingsOf("-62.00", "-156.50", "-179.25"))

        standings shouldContainExactly
            listOf(
                TeamStanding.Leader(teamId = teamId(1), tied = false, aheadOf = gap(position = 2, amount = "94.50")),
                TeamStanding.Chaser(
                    teamId = teamId(2),
                    tied = false,
                    position = 2,
                    behind = gap(position = 1, amount = "94.50"),
                    behindLeader = BigDecimal("94.50"),
                ),
                TeamStanding.Chaser(
                    teamId = teamId(3),
                    tied = false,
                    position = 3,
                    behind = gap(position = 2, amount = "22.75"),
                    behindLeader = BigDecimal("117.25"),
                ),
            )
    }

    test("no teams have no standings") {
        TeamStanding.over(emptyList()).shouldBeEmpty()
    }

    test("standings follow the order teams arrive in, ranking each by its own total") {
        val standings = TeamStanding.over(rankingsOf("10", "30", "20"))

        standings shouldContainExactly
            listOf(
                TeamStanding.Chaser(
                    teamId = teamId(1),
                    tied = false,
                    position = 3,
                    behind = gap(position = 2, amount = "10"),
                    behindLeader = BigDecimal("20"),
                ),
                TeamStanding.Leader(teamId = teamId(2), tied = false, aheadOf = gap(position = 2, amount = "10")),
                TeamStanding.Chaser(
                    teamId = teamId(3),
                    tied = false,
                    position = 2,
                    behind = gap(position = 1, amount = "10"),
                    behindLeader = BigDecimal("10"),
                ),
            )
    }

    test("rankings carry one standing per team, recomputed when a copy replaces the teams") {
        val base = Rankings(teams = rankingsOf("10", "20"), weeks = emptyList(), tournamentNames = emptyList())

        val reordered = base.copy(teams = base.teams.reversed())

        base.standings.map(TeamStanding::teamId) shouldContainExactly listOf(teamId(1), teamId(2))
        reordered.standings.map(TeamStanding::teamId) shouldContainExactly listOf(teamId(2), teamId(1))
        reordered.standings.firstOrNull() shouldBe
            TeamStanding.Leader(teamId = teamId(2), tied = false, aheadOf = gap(position = 2, amount = "10"))
    }

    test("every standing ranks its own total against all the others, with gaps upward that are positive") {
        checkAll(Arb.list(Arb.int(-2_000..2_000), 0..13)) { dollars ->
            val teams = rankingsOf(*dollars.map(Int::toString).toTypedArray())
            val standings = TeamStanding.over(teams)

            standings.map(TeamStanding::teamId) shouldContainExactly teams.map(TeamRanking::teamId)
            standings.zip(dollars).forEach { (standing, total) ->
                positionOf(standing) shouldBe 1 + dollars.count { it > total }
                standing.tied shouldBe (dollars.count { it == total } > 1)
                (standing is TeamStanding.Leader) shouldBe (total == dollars.max())
                if (standing is TeamStanding.Chaser) {
                    standing.behind.position shouldBeLessThan standing.position
                    standing.behind.amount.signum() shouldBe 1
                    standing.behindLeader shouldBe BigDecimal(dollars.max() - total)
                }
                if (standing is TeamStanding.Leader) {
                    standing.aheadOf?.position?.shouldBeGreaterThan(1)
                    standing.aheadOf?.amount?.signum()?.shouldBe(1)
                }
            }
        }
    }
})
