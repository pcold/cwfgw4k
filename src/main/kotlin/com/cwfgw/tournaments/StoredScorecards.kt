package com.cwfgw.tournaments

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A row written by a newer release, then read after a rollback, must still decode, not fail the whole report. */
private val storedScorecardJson = Json { ignoreUnknownKeys = true }

/** Holes only, never the sums: a read rebuilds each card through [RoundScorecard.of], so no stored total can drift. */
@Serializable
private data class StoredRound(
    val round: Int,
    val holes: List<StoredHole>,
)

/** Not [HoleScore], whose derived `toPar` a decode would take from the JSON rather than work out. */
@Serializable
private data class StoredHole(
    val hole: Int,
    val par: Int?,
    val strokes: Int?,
)

internal fun encodeScorecards(scorecards: List<RoundScorecard>): String =
    storedScorecardJson.encodeToString<List<StoredRound>>(
        scorecards.map { scorecard ->
            StoredRound(
                round = scorecard.round,
                holes = scorecard.holes.map { StoredHole(hole = it.hole, par = it.par, strokes = it.strokes) },
            )
        },
    )

internal fun decodeScorecards(stored: String): List<RoundScorecard> =
    storedScorecardJson.decodeFromString<List<StoredRound>>(stored).mapNotNull { round ->
        val parByHole = round.holes.associate { it.hole to it.par }
        RoundScorecard.of(
            round = round.round,
            played = round.holes.map { HoleScore(hole = it.hole, par = it.par, strokes = it.strokes) },
            unplayedPar = { hole -> parByHole[hole] },
        )
    }
