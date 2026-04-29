package com.cwfgw.scoring

import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.TeamId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.bigdecimal.shouldBeGreaterThanOrEquals
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private val DEFAULT_RULES =
    SeasonRules(
        payouts = SeasonRules.DEFAULT_PAYOUTS,
        tieFloor = SeasonRules.DEFAULT_TIE_FLOOR,
        sideBetRounds = SeasonRules.DEFAULT_SIDE_BET_ROUNDS,
        sideBetAmount = SeasonRules.DEFAULT_SIDE_BET_AMOUNT,
    )
private val ONE = BigDecimal.ONE
private val TWO = BigDecimal(2)

private fun arbBasePayout(): Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal(1000))

private fun arbPercent(): Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal(100))

class PayoutTableSpec : FunSpec({

    // ----- worked examples (anchor expected behavior) -----

    test("solo first place pays the head of the payout table") {
        PayoutTable.tieSplitPayout(position = 1, numTied = 1, multiplier = ONE, rules = DEFAULT_RULES) shouldBe
            BigDecimal(18)
    }

    test("solo position out of payout zone pays zero") {
        PayoutTable.tieSplitPayout(position = 11, numTied = 1, multiplier = ONE, rules = DEFAULT_RULES) shouldBe
            BigDecimal.ZERO
    }

    test("multiplier scales the payout") {
        PayoutTable.tieSplitPayout(position = 1, numTied = 1, multiplier = TWO, rules = DEFAULT_RULES) shouldBe
            BigDecimal(36)
    }

    test("two-way tie averages adjacent positions") {
        // T3 with 2 tied: average of positions 3,4 = ($10+$8)/2 = $9
        val payout = PayoutTable.tieSplitPayout(position = 3, numTied = 2, multiplier = ONE, rules = DEFAULT_RULES)
        payout.compareTo(BigDecimal(9)) shouldBe 0
    }

    test("three-way tie averages three adjacent positions") {
        // T4 with 3 tied: average of positions 4,5,6 = ($8+$7+$6)/3 = $7
        val payout = PayoutTable.tieSplitPayout(position = 4, numTied = 3, multiplier = ONE, rules = DEFAULT_RULES)
        payout.compareTo(BigDecimal(7)) shouldBe 0
    }

    test("tie that overlaps payout-zone tail floors at the configured tie floor") {
        // T10 with 3 tied: only position 10 ($2) is in payout zone; positions 11,12 contribute zero.
        // Average = $2/3 ≈ $0.6667 < tie floor of $1, so floored to $1.
        val payout =
            PayoutTable.tieSplitPayout(
                position = 10,
                numTied = 3,
                multiplier = ONE,
                rules = DEFAULT_RULES,
            )
        payout shouldBe ONE
    }

    // ----- team events (Zurich Classic) -----

    test("team-event solo win pays half the head-of-table per partner — team collects the solo-winner amount") {
        // ESPN reports 2 partner rows sharing position 1, so numTied=2 maps to one team-position.
        val payout =
            PayoutTable.tieSplitPayout(
                position = 1,
                numTied = 2,
                multiplier = ONE,
                rules = DEFAULT_RULES,
                isTeamEvent = true,
            )
        // Each partner: $18 / 2 = $9. Team total: $18 — same as a solo non-team winner.
        payout.compareTo(BigDecimal(9)) shouldBe 0
    }

    test("team-event two-team tie at top splits the top two payouts then halves per partner") {
        // numTied = 4 (2 teams × 2 partners) → tiedUnits = 2.
        // Avg(positions 1, 2) = ($18 + $12) / 2 = $15; per partner: $15 / 2 = $7.50.
        val payout =
            PayoutTable.tieSplitPayout(
                position = 1,
                numTied = 4,
                multiplier = ONE,
                rules = DEFAULT_RULES,
                isTeamEvent = true,
            )
        payout.compareTo(BigDecimal("7.50")) shouldBe 0
    }

    test("team-event multiplier scales the per-partner payout") {
        val payout =
            PayoutTable.tieSplitPayout(
                position = 1,
                numTied = 2,
                multiplier = TWO,
                rules = DEFAULT_RULES,
                isTeamEvent = true,
            )
        // ($18 × 2) / 2 partners = $18 each, team total $36.
        payout.compareTo(BigDecimal(18)) shouldBe 0
    }

    test("team-event still pays zero past the payout zone") {
        PayoutTable.tieSplitPayout(
            position = 11,
            numTied = 2,
            multiplier = ONE,
            rules = DEFAULT_RULES,
            isTeamEvent = true,
        ) shouldBe BigDecimal.ZERO
    }

    test("custom rules with fewer payout places truncates correctly") {
        val custom =
            SeasonRules(
                payouts = listOf(20, 10, 5).map { BigDecimal(it) },
                tieFloor = BigDecimal(2),
                sideBetRounds = emptyList(),
                sideBetAmount = BigDecimal.ZERO,
            )
        PayoutTable.tieSplitPayout(position = 1, numTied = 1, multiplier = ONE, rules = custom) shouldBe BigDecimal(20)
        PayoutTable.tieSplitPayout(position = 4, numTied = 1, multiplier = ONE, rules = custom) shouldBe BigDecimal.ZERO
    }

    // ----- property-based invariants -----

    test("splitOwnership: shares always sum exactly to base when no floor is applied") {
        checkAll(arbBasePayout(), Arb.list(arbPercent(), 1..13)) { basePayout, pcts ->
            val owners = pcts.map { pct -> TeamId(UUID.randomUUID()) to pct }
            val splits = PayoutTable.splitOwnership(basePayout, owners, floor = BigDecimal.ZERO)
            val total = splits.values.fold(BigDecimal.ZERO, BigDecimal::add)
            total.compareTo(basePayout) shouldBe 0
        }
    }

    test("splitOwnership: single owner always receives the full base regardless of pct") {
        checkAll(arbBasePayout(), arbPercent()) { basePayout, pct ->
            val team = TeamId(UUID.randomUUID())
            PayoutTable.splitOwnership(
                basePayout,
                listOf(team to pct),
                floor = BigDecimal.ZERO,
            )[team] shouldBe basePayout
        }
    }

    test("splitOwnership: every share is non-negative when pcts sum to at most 100") {
        checkAll(arbBasePayout(), Arb.int(2..13)) { basePayout, numOwners ->
            // Equal pcts that sum to exactly 100 → guarantees no remainder is negative.
            val pctEach = BigDecimal(100).divide(BigDecimal(numOwners), 4, RoundingMode.HALF_UP)
            val owners = (0 until numOwners).map { TeamId(UUID.randomUUID()) to pctEach }
            val splits = PayoutTable.splitOwnership(basePayout, owners, floor = BigDecimal.ZERO)
            splits.values.forEach { share -> share shouldBeGreaterThanOrEquals BigDecimal.ZERO }
        }
    }

    // ----- splitOwnership floor (minimum payout for any non-zero share) -----

    test("splitOwnership: 4-way 25% ownership of \$2 base bumps each share up to the \$1 floor") {
        // Without floor each owner gets $0.50; with floor=$1 each is bumped to $1.
        // Total outflow ($4) intentionally exceeds the $2 base — the floor is a
        // guarantee, not a redistribution.
        val owners = (0 until 4).map { TeamId(UUID.randomUUID()) to BigDecimal(25) }
        val splits = PayoutTable.splitOwnership(BigDecimal(2), owners, floor = ONE)
        splits.values.forEach { share -> share.compareTo(ONE) shouldBe 0 }
    }

    test("splitOwnership: single-owner halved team-event share also gets bumped to the floor") {
        // T10 single-team team event ⇒ basePayout = $2 / 2 partners = $1.00 each;
        // a 50/50 split would drop to $0.50 — bumped back to the $1 floor.
        val a = TeamId(UUID.randomUUID())
        val b = TeamId(UUID.randomUUID())
        val splits =
            PayoutTable.splitOwnership(
                basePayout = BigDecimal(1),
                owners = listOf(a to BigDecimal(50), b to BigDecimal(50)),
                floor = ONE,
            )
        splits[a]?.compareTo(ONE) shouldBe 0
        splits[b]?.compareTo(ONE) shouldBe 0
    }

    test("splitOwnership: zero base stays zero — positions outside the payout zone don't suddenly pay") {
        val owners = (0 until 4).map { TeamId(UUID.randomUUID()) to BigDecimal(25) }
        val splits = PayoutTable.splitOwnership(BigDecimal.ZERO, owners, floor = ONE)
        splits.values.forEach { share -> share.compareTo(BigDecimal.ZERO) shouldBe 0 }
    }

    test("splitOwnership: caller passes tieFloor × multiplier so a 2× event raises the floor to \$2") {
        // 4-way 25% of a $4 base (= $2 base × 2× multiplier upstream): each share
        // is $1 unfloored; floor=$2 bumps each to $2.
        val owners = (0 until 4).map { TeamId(UUID.randomUUID()) to BigDecimal(25) }
        val splits = PayoutTable.splitOwnership(BigDecimal(4), owners, floor = TWO)
        splits.values.forEach { share -> share.compareTo(TWO) shouldBe 0 }
    }

    test("splitOwnership: a share already at or above the floor is left untouched") {
        // 60/40 split of $10 with floor=$1: $6 and $4 are both above the floor,
        // total still equals base (no bumping happened).
        val a = TeamId(UUID.randomUUID())
        val b = TeamId(UUID.randomUUID())
        val splits =
            PayoutTable.splitOwnership(
                basePayout = BigDecimal(10),
                owners = listOf(a to BigDecimal(60), b to BigDecimal(40)),
                floor = ONE,
            )
        splits[a]?.compareTo(BigDecimal(6)) shouldBe 0
        splits[b]?.compareTo(BigDecimal(4)) shouldBe 0
    }

    test("tieSplitPayout: solo position in zone equals payouts[position-1] × multiplier") {
        checkAll(
            Arb.int(1..SeasonRules.DEFAULT_PAYOUTS.size),
            Arb.int(0..5),
        ) { position, multiplierInt ->
            val multiplier = BigDecimal(multiplierInt)
            val expected = SeasonRules.DEFAULT_PAYOUTS[position - 1].multiply(multiplier)
            PayoutTable.tieSplitPayout(position, 1, multiplier, DEFAULT_RULES) shouldBe expected
        }
    }

    test("tieSplitPayout: position past the payout zone always pays zero") {
        checkAll(
            Arb.int(SeasonRules.DEFAULT_PAYOUTS.size + 1..1000),
            Arb.int(1..20),
            Arb.int(0..10),
        ) { position, numTied, multiplierInt ->
            PayoutTable.tieSplitPayout(
                position,
                numTied,
                BigDecimal(multiplierInt),
                DEFAULT_RULES,
            ) shouldBe BigDecimal.ZERO
        }
    }

    test("tieSplitPayout: payout is never negative for any reasonable input") {
        checkAll(
            Arb.int(1..50),
            Arb.int(1..20),
            Arb.int(0..10),
        ) { position, numTied, multiplierInt ->
            val payout =
                PayoutTable.tieSplitPayout(position, numTied, BigDecimal(multiplierInt), DEFAULT_RULES)
            payout shouldBeGreaterThanOrEquals BigDecimal.ZERO
        }
    }
})
