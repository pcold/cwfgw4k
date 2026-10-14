package com.cwfgw.seasons

/** Refused before any row is written. Without `rules` the defaults apply, so only the flat fields can violate. */
internal sealed interface CreateSeasonViolation {
    /** An empty list writes no payout rows, so the season would silently read back the default payouts. */
    data object NoPayoutPlaces : CreateSeasonViolation

    /** An empty list writes no rows, so the season would silently read back the default rounds. */
    data object NoSideBetRounds : CreateSeasonViolation

    /** The UNIQUE (season_id, round) constraint would refuse it anyway, as a 500. */
    data object DuplicateSideBetRound : CreateSeasonViolation

    /**
     * Counts as staked, so settlement would pay the losers; zero means no side bets and stays allowed. The
     * CHECK `seasons_side_bet_amount_non_negative` would refuse it anyway, as a 500.
     */
    data object NegativeSideBetAmount : CreateSeasonViolation
}

// Both amount fields are checked, not only the one the repository writes, so the guard needn't track its precedence.
internal fun CreateSeasonRequest.violation(): CreateSeasonViolation? =
    if (listOfNotNull(sideBetAmount, rules?.sideBetAmount).any { it.signum() < 0 }) {
        CreateSeasonViolation.NegativeSideBetAmount
    } else {
        rules?.violation()
    }

private fun SeasonRules.violation(): CreateSeasonViolation? =
    when {
        payouts.isEmpty() -> CreateSeasonViolation.NoPayoutPlaces
        sideBetRounds.isEmpty() -> CreateSeasonViolation.NoSideBetRounds
        sideBetRounds.toSet().size != sideBetRounds.size -> CreateSeasonViolation.DuplicateSideBetRound
        else -> null
    }
