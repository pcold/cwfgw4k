package com.cwfgw.espn

import com.cwfgw.tournaments.HoleScore
import com.cwfgw.tournaments.RoundScorecard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** Port Royal's pars, holes 1 to 18. */
private val COURSE_PARS = listOf(4, 5, 3, 4, 4, 4, 5, 3, 4, 4, 4, 4, 3, 4, 4, 3, 5, 4)

private fun parOf(hole: Int): Int = COURSE_PARS[hole - 1]

/** One hole as ESPN writes it; [toPar] defaults to what [strokes] scores on the course. */
private fun espnHole(
    number: Int,
    strokes: Int,
    toPar: String = scoreToParText(strokes - parOf(number)),
): String = """{"period": $number, "value": $strokes.0, "scoreType": {"displayValue": "$toPar"}}"""

private fun scoreToParText(toPar: Int): String =
    when {
        toPar == 0 -> "E"
        toPar > 0 -> "+$toPar"
        else -> "$toPar"
    }

private fun round(
    number: Int,
    holes: List<String>,
): String = """{"period": $number, "value": 70.0, "linescores": [${holes.joinToString(",")}]}"""

/** Every hole of [holeNumbers] played in that order, at par. */
private fun parsOn(holeNumbers: Iterable<Int>): List<String> = holeNumbers.map { espnHole(it, parOf(it)) }

private fun player(
    id: String,
    rounds: List<String>,
    name: String = "Player $id",
): String =
    """
    {
        "id": "$id",
        "order": ${id.toInt()},
        "score": "E",
        "athlete": {"displayName": "$name"},
        "linescores": [${rounds.joinToString(",")}],
        "status": {"type": {"id": "1"}}
    }
    """.trimIndent()

private fun scorecardsOf(vararg competitors: String): Map<String, List<RoundScorecard>> =
    parseScoreboard(
        """
        {
          "events": [{
            "id": "401850917",
            "name": "Butterfield Bermuda Championship",
            "status": {"type": {"completed": false}},
            "competitions": [{"competitors": [${competitors.joinToString(",")}]}]
          }]
        }
        """.trimIndent(),
    ).single().competitors.associate { it.name to it.scorecards }

private fun RoundScorecard.hole(number: Int): HoleScore = holes.single { it.hole == number }

class EspnScorecardParseSpec : FunSpec({
    test("a full round reads every hole's par and strokes off ESPN's score to par") {
        val birdieOnTwo = parsOn(listOf(1)) + espnHole(2, strokes = 4) + parsOn(3..18)

        val scorecard = scorecardsOf(player("1", listOf(round(1, birdieOnTwo)))).getValue("Player 1").single()

        scorecard.holes.map(HoleScore::par) shouldContainExactly COURSE_PARS
        scorecard.hole(2) shouldBe HoleScore(hole = 2, par = 5, strokes = 4)
        scorecard.frontNine shouldBe 35
        scorecard.backNine shouldBe 35
        scorecard.total shouldBe 70
    }

    test("a round begun on the tenth tee lists its holes by number, the front nine unplayed at the field's par") {
        val tenthTee = player("1", listOf(round(1, parsOn(10..12))))
        val firstTee = player("2", listOf(round(1, parsOn(1..9))))

        val scorecard = scorecardsOf(tenthTee, firstTee).getValue("Player 1").single()

        scorecard.holes.map(HoleScore::hole) shouldContainExactly (1..18).toList()
        scorecard.hole(1) shouldBe HoleScore(hole = 1, par = 4, strokes = null)
        scorecard.frontNine.shouldBeNull()
        scorecard.backNine shouldBe 12
    }

    test("a hole ESPN scores OTHER takes the par most of the field shows for it that round") {
        val quadruple = player("1", listOf(round(1, listOf(espnHole(3, strokes = 7, toPar = "OTHER")))))
        val pars = player("2", listOf(round(1, parsOn(listOf(3)))))
        val misreadPar = player("3", listOf(round(1, listOf(espnHole(3, strokes = 4, toPar = "E")))))
        val anotherPar = player("4", listOf(round(1, parsOn(listOf(3)))))

        val scorecard = scorecardsOf(quadruple, pars, misreadPar, anotherPar).getValue("Player 1").single()

        scorecard.hole(3) shouldBe HoleScore(hole = 3, par = 3, strokes = 7)
        scorecard.hole(3).toPar shouldBe 4
    }

    test("a hole the field splits evenly between two pars takes the lower, whichever order ESPN lists them in") {
        val quadruple = player("1", listOf(round(1, listOf(espnHole(3, strokes = 7, toPar = "OTHER")))))
        val parThree = player("2", listOf(round(1, parsOn(listOf(3)))))
        val parFour = player("3", listOf(round(1, listOf(espnHole(3, strokes = 4, toPar = "E")))))

        scorecardsOf(quadruple, parFour, parThree).getValue("Player 1").single().hole(3).par shouldBe 3
        scorecardsOf(quadruple, parThree, parFour).getValue("Player 1").single().hole(3).par shouldBe 3
    }

    test("a hole nobody has reached this round takes its par from the other rounds, and none from no round") {
        val leader = player("1", listOf(round(1, parsOn(1..17)), round(2, parsOn(listOf(1)))))

        val roundTwo = scorecardsOf(leader).getValue("Player 1").single { it.round == 2 }

        roundTwo.hole(17).par shouldBe 5
        roundTwo.hole(18).par.shouldBeNull()
    }

    test("a round ESPN sends without its holes has no scorecard, and the rest stay in round order") {
        val holeless = """{"period": 2, "value": 68.0}"""
        val notStarted = """{"period": 3}"""
        val rounds = listOf(round(3, parsOn(1..2)), holeless, round(1, parsOn(1..18)), notStarted)

        val scorecards = scorecardsOf(player("1", rounds)).getValue("Player 1")

        scorecards.map(RoundScorecard::round) shouldContainExactly listOf(1, 3)
    }

    test("a player with no round begun has no scorecards") {
        scorecardsOf(player("1", emptyList())).getValue("Player 1").shouldBeEmpty()
    }

    test("a team entry's partners share the pair's scorecards") {
        val pair =
            """
            {
                "id": "131066",
                "type": "team",
                "order": 1,
                "score": "E",
                "team": {"displayName": "Novak/Griffin"},
                "linescores": [${round(1, parsOn(1..9))}],
                "status": {"type": {"id": "1"}}
            }
            """.trimIndent()

        val scorecards = scorecardsOf(pair)

        scorecards.getValue("Novak") shouldBe scorecards.getValue("Griffin")
        scorecards.getValue("Novak").single().frontNine shouldBe 36
    }
})
