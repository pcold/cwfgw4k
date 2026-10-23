package com.cwfgw.tournaments

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

private const val PAR_FOUR = 4

private fun played(
    hole: Int,
    strokes: Int,
    par: Int? = PAR_FOUR,
): HoleScore = HoleScore(hole = hole, par = par, strokes = strokes)

private fun card(vararg holes: HoleScore): RoundScorecard? =
    RoundScorecard.of(round = 1, played = holes.toList()) { PAR_FOUR }

class RoundScorecardSpec : FunSpec({
    test("a hole's toPar is its strokes less its par") {
        played(hole = 1, strokes = 3).toPar shouldBe -1
    }

    test("a hole's toPar is null while its par or its strokes are unknown") {
        played(hole = 1, strokes = 5, par = null).toPar.shouldBeNull()
        HoleScore(hole = 1, par = PAR_FOUR, strokes = null).toPar.shouldBeNull()
    }

    test("a round with no hole played has no scorecard") {
        card().shouldBeNull()
        card(HoleScore(hole = 1, par = PAR_FOUR, strokes = null)).shouldBeNull()
        card(played(hole = 19, strokes = 4)).shouldBeNull()
    }

    test("a scorecard lists holes 1 to 18 in order, the unplayed ones at the par it is told and no strokes") {
        val scorecard = card(played(hole = 12, strokes = 5), played(hole = 10, strokes = 3)).shouldNotBeNull()

        scorecard.holes.map(HoleScore::hole) shouldContainExactly (1..18).toList()
        scorecard.holes.single { it.hole == 10 }.strokes shouldBe 3
        scorecard.holes.single { it.hole == 1 } shouldBe HoleScore(hole = 1, par = PAR_FOUR, strokes = null)
    }

    test("the nine and total sums count only holes played, null for a nine not yet begun") {
        val scorecard = card(played(hole = 10, strokes = 3), played(hole = 11, strokes = 5)).shouldNotBeNull()

        scorecard.frontNine.shouldBeNull()
        scorecard.backNine shouldBe 8
        scorecard.total shouldBe 8
    }

    test("each nine's par counts every hole, played or not, and the total both nines") {
        val scorecard = card(played(hole = 10, strokes = 3)).shouldNotBeNull()

        scorecard.frontPar shouldBe 36
        scorecard.backPar shouldBe 36
        scorecard.totalPar shouldBe 72
    }

    test("a nine's par is null while any of its holes' par is unknown, and so is the total") {
        val scorecard =
            RoundScorecard.of(round = 1, played = listOf(played(hole = 1, strokes = 4))) { hole ->
                if (hole == 12) null else PAR_FOUR
            }.shouldNotBeNull()

        scorecard.frontPar shouldBe 36
        scorecard.backPar.shouldBeNull()
        scorecard.totalPar.shouldBeNull()
    }

    test("a played hole's own par counts toward its nine's par") {
        val scorecard = card(played(hole = 3, strokes = 3, par = 3)).shouldNotBeNull()

        scorecard.frontPar shouldBe 35
    }

    test("of two entries for one hole the first is kept") {
        val scorecard = card(played(hole = 1, strokes = 3), played(hole = 1, strokes = 6)).shouldNotBeNull()

        scorecard.holes.single { it.hole == 1 }.strokes shouldBe 3
    }

    test("a hole outside 1 to 18 is dropped") {
        val scorecard = card(played(hole = 0, strokes = 3), played(hole = 2, strokes = 4)).shouldNotBeNull()

        scorecard.total shouldBe 4
    }

    test("the front and back nine always add up to the total") {
        val holeStrokes = Arb.list(Arb.int(1..18).map { hole -> played(hole = hole, strokes = hole % 3 + 3) }, 1..30)
        checkAll(holeStrokes) { holes ->
            val scorecard = card(*holes.toTypedArray()).shouldNotBeNull()

            (scorecard.frontNine ?: 0) + (scorecard.backNine ?: 0) shouldBe scorecard.total
            scorecard.total shouldBe holes.distinctBy(HoleScore::hole).sumOf { it.strokes ?: 0 }
        }
    }
})
