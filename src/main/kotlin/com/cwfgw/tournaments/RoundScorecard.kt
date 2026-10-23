package com.cwfgw.tournaments

import kotlinx.serialization.Serializable

private const val HOLES_PER_ROUND = 18

private const val LAST_FRONT_NINE_HOLE = 9

/** [par] is null for a hole nobody in the field has shown a par for yet; [toPar] is null until both are known. */
@Serializable
data class HoleScore(
    val hole: Int,
    val par: Int?,
    val strokes: Int?,
) {
    val toPar: Int? = if (strokes != null && par != null) strokes - par else null
}

/**
 * [holes] is always 1–18 by hole number, whatever order they were played in. [frontNine], [backNine] and [total] sum
 * only the holes played, and are null before the first of them. [frontPar], [backPar] and [totalPar] are null while
 * any of their holes' par is unknown, since a partial par would read as a short course.
 */
@ConsistentCopyVisibility
@Serializable
data class RoundScorecard private constructor(
    val round: Int,
    val holes: List<HoleScore>,
) {
    val frontNine: Int? = holes.filter { it.hole <= LAST_FRONT_NINE_HOLE }.strokesPlayed()
    val backNine: Int? = holes.filter { it.hole > LAST_FRONT_NINE_HOLE }.strokesPlayed()
    val total: Int? = holes.strokesPlayed()
    val frontPar: Int? = holes.filter { it.hole <= LAST_FRONT_NINE_HOLE }.parOfEveryHole()
    val backPar: Int? = holes.filter { it.hole > LAST_FRONT_NINE_HOLE }.parOfEveryHole()
    val totalPar: Int? = holes.parOfEveryHole()

    companion object {
        /**
         * Null when [played] holds no hole from 1 to 18. A hole outside that range is dropped, and of two entries for
         * one hole the first is kept. [unplayedPar] names the par of each hole [played] lacks.
         */
        fun of(
            round: Int,
            played: List<HoleScore>,
            unplayedPar: (hole: Int) -> Int?,
        ): RoundScorecard? {
            val playedByHole =
                played
                    .filter { it.hole in 1..HOLES_PER_ROUND && it.strokes != null }
                    .distinctBy(HoleScore::hole)
                    .associateBy(HoleScore::hole)
            if (playedByHole.isEmpty()) return null
            val holes =
                (1..HOLES_PER_ROUND).map { hole ->
                    playedByHole[hole] ?: HoleScore(hole = hole, par = unplayedPar(hole), strokes = null)
                }
            return RoundScorecard(round = round, holes = holes)
        }
    }
}

private fun List<HoleScore>.strokesPlayed(): Int? = mapNotNull(HoleScore::strokes).takeIf { it.isNotEmpty() }?.sum()

private fun List<HoleScore>.parOfEveryHole(): Int? = mapNotNull(HoleScore::par).takeIf { it.size == size }?.sum()
