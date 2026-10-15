package com.cwfgw.seasons

import java.math.BigDecimal

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

    /** Charges that place instead of paying it; zero pays nothing. A CHECK would refuse it anyway, as a 500. */
    data object NegativePayout : CreateSeasonViolation

    /** Floors nothing, the same as zero, which stays legal. A CHECK would refuse it anyway, as a 500. */
    data object NegativeTieFloor : CreateSeasonViolation

    /** A cap that admits no team. A CHECK would refuse it anyway, as a 500. */
    data object MaxTeamsBelowOne : CreateSeasonViolation

    /** At [RULE_AMOUNT_OVERFLOW] the NUMERIC(10,4) column would refuse it anyway, as a 500. */
    data object PayoutTooLarge : CreateSeasonViolation

    /** At [RULE_AMOUNT_OVERFLOW] the NUMERIC(10,4) column would refuse it anyway, as a 500. */
    data object TieFloorTooLarge : CreateSeasonViolation

    /** At [RULE_AMOUNT_OVERFLOW] the NUMERIC(10,4) column would refuse it anyway, as a 500. */
    data object SideBetAmountTooLarge : CreateSeasonViolation
}

// Must match the character class in seasons_name_not_blank (V018). A no-break space and other non-ASCII spaces pass
// both, and are left to the UI's trim().
private const val NAME_PADDING = " \t\n\r\u000B\u000C"

// The least value NUMERIC(10,4) rounds up to 10^6. Compared rather than setScale(4)'d, which would allocate the full
// magnitude of an amount like 1e1000000000.
private val RULE_AMOUNT_OVERFLOW = BigDecimal("999999.99995")

internal fun CreateSeasonRequest.violation(): CreateSeasonViolation? =
    when {
        name.all { it in NAME_PADDING } -> CreateSeasonViolation.BlankName
        maxTeams != null && maxTeams < 1 -> CreateSeasonViolation.MaxTeamsBelowOne
        else -> seasonAmountViolation() ?: rules?.violation()
    }

// Both fields of each amount, not only the one the repository writes, so the guard needn't track their precedence.
private fun CreateSeasonRequest.seasonAmountViolation(): CreateSeasonViolation? =
    listOfNotNull(sideBetAmount, rules?.sideBetAmount).amountViolation(
        negative = CreateSeasonViolation.NegativeSideBetAmount,
        tooLarge = CreateSeasonViolation.SideBetAmountTooLarge,
    ) ?: listOfNotNull(tieFloor, rules?.tieFloor).amountViolation(
        negative = CreateSeasonViolation.NegativeTieFloor,
        tooLarge = CreateSeasonViolation.TieFloorTooLarge,
    )

private fun SeasonRules.violation(): CreateSeasonViolation? =
    when {
        payouts.isEmpty() -> CreateSeasonViolation.NoPayoutPlaces
        sideBetRounds.isEmpty() -> CreateSeasonViolation.NoSideBetRounds
        sideBetRounds.toSet().size != sideBetRounds.size -> CreateSeasonViolation.DuplicateSideBetRound
        else ->
            payouts.amountViolation(
                negative = CreateSeasonViolation.NegativePayout,
                tooLarge = CreateSeasonViolation.PayoutTooLarge,
            )
    }

private fun List<BigDecimal>.amountViolation(
    negative: CreateSeasonViolation,
    tooLarge: CreateSeasonViolation,
): CreateSeasonViolation? =
    when {
        any { it.signum() < 0 } -> negative
        any { it >= RULE_AMOUNT_OVERFLOW } -> tooLarge
        else -> null
    }
