package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import java.time.LocalDate

/**
 * [statuses] defaults to every status, so "any" is never spelled as an empty set: empty matches nothing, as
 * `IN ()` does. [startsBefore] and [startsAfter] are strict, so the tournament on that date is never included.
 */
data class TournamentFilter(
    val seasonId: SeasonId? = null,
    val statuses: Set<TournamentStatus> = TournamentStatus.entries.toSet(),
    val startsBefore: LocalDate? = null,
    val startsAfter: LocalDate? = null,
)
