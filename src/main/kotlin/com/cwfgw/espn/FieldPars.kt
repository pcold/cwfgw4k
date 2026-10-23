package com.cwfgw.espn

import com.cwfgw.tournaments.HoleScore
import com.cwfgw.tournaments.RoundScorecard

/**
 * The par the field most often shows on each hole, for a hole whose to-par ESPN wrote as "OTHER" or that isn't played
 * yet; a hole nobody has reached in a round borrows from the other rounds. Can be wrong in a course rotation, where
 * one round is split across courses, but only on those two kinds of hole.
 */
internal class FieldPars private constructor(
    private val byRoundAndHole: Map<Pair<Int, Int>, Int>,
    private val byHole: Map<Int, Int>,
) {
    fun parOf(
        round: Int,
        hole: Int,
    ): Int? = byRoundAndHole[round to hole] ?: byHole[hole]

    companion object {
        fun of(competitors: List<EspnCompetitorJson>): FieldPars {
            val readings =
                competitors.flatMap { competitor ->
                    competitor.rounds().flatMap { (round, holes) ->
                        holes.mapNotNull { hole -> hole.par?.let { par -> ParReading(round, hole.hole, par) } }
                    }
                }
            return FieldPars(
                byRoundAndHole =
                    readings
                        .groupBy({ it.round to it.hole }, ParReading::par)
                        .mapValues { (_, pars) -> pars.mostCommon() },
                byHole =
                    readings.groupBy(ParReading::hole, ParReading::par).mapValues { (_, pars) ->
                        pars.mostCommon()
                    },
            )
        }
    }
}

internal fun EspnCompetitorJson.scorecards(fieldPars: FieldPars): List<RoundScorecard> =
    rounds()
        .mapNotNull { (round, holes) ->
            RoundScorecard.of(
                round = round,
                played =
                    holes.map { hole ->
                        HoleScore(
                            hole = hole.hole,
                            par = hole.par ?: fieldPars.parOf(round, hole.hole),
                            strokes = hole.strokes,
                        )
                    },
                unplayedPar = { hole -> fieldPars.parOf(round, hole) },
            )
        }.sortedBy(RoundScorecard::round)

/** [toPar] is null where ESPN wrote "OTHER", so [par] can't be read off this hole alone. */
private data class EspnHole(
    val hole: Int,
    val strokes: Int,
    val toPar: Int?,
) {
    val par: Int? get() = toPar?.let { strokes - it }
}

private data class ParReading(
    val round: Int,
    val hole: Int,
    val par: Int,
)

private fun EspnCompetitorJson.rounds(): List<Pair<Int, List<EspnHole>>> =
    linescores.mapIndexed { index, round ->
        (round.period ?: (index + 1)) to round.linescores.mapNotNull(EspnHoleJson::parsed)
    }

private fun EspnHoleJson.parsed(): EspnHole? {
    val hole = period ?: return null
    val strokes = value?.toInt() ?: return null
    return EspnHole(hole = hole, strokes = strokes, toPar = scoreType?.displayValue?.let(::parseScoreToPar))
}

/** A tie goes to the lower par, so the answer never hangs on the order ESPN lists the field in. */
private fun List<Int>.mostCommon(): Int =
    groupingBy { it }
        .eachCount()
        .entries
        .maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        .key
