package com.cwfgw.espn

import com.cwfgw.golfers.Golfer
import com.cwfgw.tournaments.TournamentId
import kotlinx.serialization.Serializable

/**
 * Each golfer is listed once, under its most severe kind: a created golfer that twins a rostered surname is in
 * [rosteredSurnameTwins], not [createdGolfers]. A competitor that ends up with a result row is never unplaced.
 */
@Serializable
data class EspnImportWarnings(
    val tournamentId: TournamentId,
    val tournamentName: String,
    val rosteredSurnameTwins: List<RosteredSurnameTwin>,
    val createdGolfers: List<Golfer>,
    val unmatchedCompetitors: List<UnmatchedCompetitor>,
    val contestedCompetitors: List<ContestedCompetitor>,
) {
    val isEmpty: Boolean
        get() =
            rosteredSurnameTwins.isEmpty() && createdGolfers.isEmpty() &&
                unmatchedCompetitors.isEmpty() && contestedCompetitors.isEmpty()
}

/** The Koivun signature: results credited to [created] may belong to one of [rostered], whose team then scores none. */
@Serializable
data class RosteredSurnameTwin(
    val created: Golfer,
    val rostered: List<Golfer>,
)

/** Got no result row and no golfer was created for it: a competitor ESPN sent without a name. */
@Serializable
data class UnmatchedCompetitor(
    val espnCompetitorId: String,
    val name: String,
)

/** Got no result row because names can't tell which of [candidates] it is, and a new golfer would twin that one. */
@Serializable
data class ContestedCompetitor(
    val espnCompetitorId: String,
    val name: String,
    val candidates: List<Golfer>,
)
