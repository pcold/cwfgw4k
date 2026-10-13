package com.cwfgw.reports

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.math.BigDecimal

private fun arbDollarsAndCents(): Arb<BigDecimal> =
    Arb.int(-50_000..50_000).map { cents -> BigDecimal(cents).movePointLeft(2) }

class WonLostTotalsSpec : FunSpec({

    test("over sums the gains into won and the losses into lost, and nets the two") {
        val amounts = listOf(BigDecimal(5), BigDecimal(-3), BigDecimal.ZERO, BigDecimal("2.50"), BigDecimal(-1))

        val totals = WonLostTotals.over(amounts)

        totals.won shouldBe BigDecimal("7.50")
        totals.lost shouldBe BigDecimal(-4)
        totals.net shouldBe BigDecimal("3.50")
    }

    test("over amounts that are all losses has won nothing") {
        val totals = WonLostTotals.over(listOf(BigDecimal(-3), BigDecimal(-4)))

        totals.won shouldBe BigDecimal.ZERO
        totals.lost shouldBe BigDecimal(-7)
        totals.net shouldBe BigDecimal(-7)
    }

    test("over no amounts is zero won, lost and net") {
        val totals = WonLostTotals.over(emptyList())

        totals.won shouldBe BigDecimal.ZERO
        totals.lost shouldBe BigDecimal.ZERO
        totals.net shouldBe BigDecimal.ZERO
    }

    test("over nets to the plain sum of its amounts, with won never negative and lost never positive") {
        checkAll(Arb.list(arbDollarsAndCents(), 0..13)) { amounts ->
            val totals = WonLostTotals.over(amounts)

            totals.net.compareTo(amounts.fold(BigDecimal.ZERO, BigDecimal::add)) shouldBe 0
            totals.won.signum() shouldBeGreaterThanOrEqual 0
            totals.lost.signum() shouldBeLessThanOrEqual 0
        }
    }
})
