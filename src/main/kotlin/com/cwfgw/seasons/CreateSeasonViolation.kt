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

    /** Spaces only, as `btrim` trims, so it agrees with the CHECK, which would refuse it anyway, as a 500. */
    data object BlankName : CreateSeasonViolation
}

// Both amount fields are checked, not only the one the repository writes, so the guard needn't track its precedence.
internal fun CreateSeasonRequest.violation(): CreateSeasonViolation? =
    when {
        name.trim(' ').isEmpty() -> CreateSeasonViolation.BlankName
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
