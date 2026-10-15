package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import java.time.LocalDate

/**
 * "Any status" is the full default set, never an empty one: empty [statuses] matches nothing, as `IN ()` does.
 * [startsBefore] and [startsAfter] are strict: a tournament starting on that date is excluded.
 */
data class TournamentFilter(
    val seasonId: SeasonId? = null,
    val statuses: Set<TournamentStatus> = TournamentStatus.entries.toSet(),
    val startsBefore: LocalDate? = null,
    val startsAfter: LocalDate? = null,
    val startsOn: LocalDate? = null,
)
