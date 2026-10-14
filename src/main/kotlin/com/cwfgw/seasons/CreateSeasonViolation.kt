package com.cwfgw.seasons

/** Refused before any row is written. A request without `rules` has none: the defaults then apply. */
internal sealed interface CreateSeasonViolation {
    /** An empty list writes no payout rows, so the season would silently read back the default payouts. */
    data object NoPayoutPlaces : CreateSeasonViolation

    /** An empty list writes no rows, so the season would silently read back the default rounds. */
    data object NoSideBetRounds : CreateSeasonViolation

    /** The UNIQUE (season_id, round) constraint would refuse it anyway, as a 500. */
    data object DuplicateSideBetRound : CreateSeasonViolation
}

internal fun CreateSeasonRequest.violation(): CreateSeasonViolation? {
    val submitted = rules ?: return null
    return when {
        submitted.payouts.isEmpty() -> CreateSeasonViolation.NoPayoutPlaces
        submitted.sideBetRounds.isEmpty() -> CreateSeasonViolation.NoSideBetRounds
        submitted.sideBetRounds.toSet().size != submitted.sideBetRounds.size ->
            CreateSeasonViolation.DuplicateSideBetRound
        else -> null
    }
}
