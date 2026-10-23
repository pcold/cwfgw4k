package com.cwfgw.tournaments

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

private val PARS = listOf(4, 5, 3, 4, 4, 4, 3, 5, 4, 4, 4, 3, 5, 4, 4, 3, 4, 5)

private fun roundOf(
    round: Int,
    strokes: List<Int>,
): RoundScorecard? =
    RoundScorecard.of(
        round = round,
        played = strokes.mapIndexed { index, taken -> HoleScore(hole = index + 1, par = PARS[index], strokes = taken) },
        unplayedPar = { hole -> PARS.getOrNull(hole - 1) },
    )

class StoredScorecardsSpec : FunSpec({
    test("a round trip keeps every hole and works the sums out again") {
        val cards = listOfNotNull(roundOf(1, PARS.map { it - 1 }), roundOf(2, listOf(4, 6, 3)))

        val decoded = decodeScorecards(encodeScorecards(cards))

        decoded shouldContainExactly cards
        decoded.map { it.total } shouldContainExactly listOf(PARS.sum() - PARS.size, 13)
        decoded.last().holes.last() shouldBe HoleScore(hole = 18, par = 5, strokes = null)
    }

    test("the stored JSON holds holes alone, so no stored sum can disagree with them") {
        val stored = encodeScorecards(listOfNotNull(roundOf(1, listOf(3))))

        stored shouldNotContain "toPar"
        stored shouldNotContain "total"
        stored shouldNotContain "frontNine"
    }

    test("a field a newer release stored is ignored rather than failing the read") {
        val decoded = decodeScorecards("""[{"round":1,"tee":10,"holes":[{"hole":1,"par":4,"strokes":3,"putts":1}]}]""")

        decoded.single().holes.firstOrNull() shouldBe HoleScore(hole = 1, par = 4, strokes = 3)
    }

    test("a row stored before scorecards were saved decodes to none") {
        decodeScorecards("[]").shouldBeEmpty()
    }

    test("a stored round with no hole played decodes to no card, as RoundScorecard.of refuses it") {
        decodeScorecards("""[{"round":1,"holes":[{"hole":1,"par":4,"strokes":null}]}]""").shouldBeEmpty()
    }
})
