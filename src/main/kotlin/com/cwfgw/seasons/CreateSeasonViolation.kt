package com.cwfgw.seasons

/** A field of a [CreateSeasonRequest] the server refuses before any row is written. */
sealed interface CreateSeasonViolation {
    /** @see CreateSeasonViolation */
    data object BlankName : CreateSeasonViolation

    /** An empty list would write no payout rows, and the season would read back the defaults instead. */
    data object NoPayoutPlaces : CreateSeasonViolation
}

fun CreateSeasonRequest.violation(): CreateSeasonViolation? =
    when {
        name.isBlank() -> CreateSeasonViolation.BlankName
        rules?.payouts?.isEmpty() == true -> CreateSeasonViolation.NoPayoutPlaces
        else -> null
    }
