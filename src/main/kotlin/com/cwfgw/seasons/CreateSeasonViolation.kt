package com.cwfgw.seasons

/** Refused before any row is written. Without `rules`, payouts and rounds default and go unchecked. */
internal sealed interface CreateSeasonViolation {
    /** An empty list writes no payout rows, so the season would silently read back the default payouts. */
    data object NoPayoutPlaces : CreateSeasonViolation

    /** An empty list writes no rows, so the season would silently read back the default rounds. */
    data object NoSideBetRounds : CreateSeasonViolation

    /** The UNIQUE (season_id, round) constraint would refuse it anyway, as a 500. */
    data object DuplicateSideBetRound : CreateSeasonViolation

    /** Settles inverted, paying the losers; zero means no side bets. A CHECK would refuse it anyway, as a 500. */
    data object NegativeSideBetAmount : CreateSeasonViolation

    /** Nothing but [NAME_PADDING]. The CHECK would refuse it anyway, as a 500. */
    data object BlankName : CreateSeasonViolation
}

// Must match the character class in seasons_name_not_blank (V018). A no-break space and other non-ASCII spaces pass
// both, and are left to the UI's trim().
private const val NAME_PADDING = " \t\n\r\u000B\u000C"

internal fun CreateSeasonRequest.violation(): CreateSeasonViolation? =
    when {
        name.all { it in NAME_PADDING } -> CreateSeasonViolation.BlankName
        // Both amount fields, not only the one the repository writes, so the guard needn't track their precedence.
        listOfNotNull(sideBetAmount, rules?.sideBetAmount).any { it.signum() < 0 } ->
            CreateSeasonViolation.NegativeSideBetAmount
        else -> rules?.violation()
    }

private fun SeasonRules.violation(): CreateSeasonViolation? =
    when {
        payouts.isEmpty() -> CreateSeasonViolation.NoPayoutPlaces
        sideBetRounds.isEmpty() -> CreateSeasonViolation.NoSideBetRounds
        sideBetRounds.toSet().size != sideBetRounds.size -> CreateSeasonViolation.DuplicateSideBetRound
        else -> null
    }
