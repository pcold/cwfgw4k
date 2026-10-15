package com.cwfgw.seasons

import java.math.BigDecimal

/** Refused before any row is written. Without `rules`, every rule value defaults and goes unchecked. */
internal sealed interface CreateSeasonViolation {
    /** One variant per field and bound rather than a field parameter, so each stays a distinct refusal. */
    sealed interface RuleAmount : CreateSeasonViolation

    /** An empty list writes no payout rows, so the season would silently read back the default payouts. */
    data object NoPayoutPlaces : CreateSeasonViolation

    /** An empty list writes no rows, so the season would silently read back the default rounds. */
    data object NoSideBetRounds : CreateSeasonViolation

    /** The UNIQUE (season_id, round) constraint would refuse it anyway, as a 500. */
    data object DuplicateSideBetRound : CreateSeasonViolation

    /** Settles inverted, paying the losers; zero means no side bets. A CHECK would refuse it anyway, as a 500. */
    data object NegativeSideBetAmount : RuleAmount

    /** Nothing but [NAME_PADDING]. The CHECK would refuse it anyway, as a 500. */
    data object BlankName : CreateSeasonViolation

    /** Charges that place instead of paying it; zero pays nothing. A CHECK would refuse it anyway, as a 500. */
    data object NegativePayout : RuleAmount

    /** Floors nothing, the same as zero, which stays legal. A CHECK would refuse it anyway, as a 500. */
    data object NegativeTieFloor : RuleAmount

    /** A cap that admits no team. A CHECK would refuse it anyway, as a 500. */
    data object MaxTeamsBelowOne : CreateSeasonViolation

    /** At [RULE_AMOUNT_OVERFLOW] the NUMERIC(10,4) column would refuse it anyway, as a 500. */
    data object PayoutTooLarge : RuleAmount

    /** At [RULE_AMOUNT_OVERFLOW] the NUMERIC(10,4) column would refuse it anyway, as a 500. */
    data object TieFloorTooLarge : RuleAmount

    /** At [RULE_AMOUNT_OVERFLOW] the NUMERIC(10,4) column would refuse it anyway, as a 500. */
    data object SideBetAmountTooLarge : RuleAmount

    /** Past [MONEY_SCALE] places NUMERIC(10,4) would round it silently, and past 16383 refuse it as a 500. */
    data object PayoutTooManyDecimalPlaces : RuleAmount

    /** Past [MONEY_SCALE] places NUMERIC(10,4) would round it silently, and past 16383 refuse it as a 500. */
    data object TieFloorTooManyDecimalPlaces : RuleAmount

    /** Past [MONEY_SCALE] places NUMERIC(10,4) would round it silently, and past 16383 refuse it as a 500. */
    data object SideBetAmountTooManyDecimalPlaces : RuleAmount
}

// Must match the character class in seasons_name_not_blank (V018). A no-break space and other non-ASCII spaces pass
// both, and are left to the UI's trim().
private const val NAME_PADDING = " \t\n\r\u000B\u000C"

// The least value NUMERIC(10,4) rounds up to 10^6. Compared rather than setScale(4)'d, which would allocate the full
// magnitude of an amount like 1e1000000000.
private val RULE_AMOUNT_OVERFLOW = BigDecimal("999999.99995")

private const val MONEY_SCALE = 4

internal fun CreateSeasonRequest.violation(): CreateSeasonViolation? =
    when {
        name.all { it in NAME_PADDING } -> CreateSeasonViolation.BlankName
        maxTeams != null && maxTeams < 1 -> CreateSeasonViolation.MaxTeamsBelowOne
        else -> rules?.let { it.seasonAmountViolation() ?: it.sideTableViolation() }
    }

private fun SeasonRules.seasonAmountViolation(): CreateSeasonViolation? =
    listOf(sideBetAmount).amountViolation(
        negative = CreateSeasonViolation.NegativeSideBetAmount,
        tooLarge = CreateSeasonViolation.SideBetAmountTooLarge,
        tooManyDecimalPlaces = CreateSeasonViolation.SideBetAmountTooManyDecimalPlaces,
    ) ?: listOf(tieFloor).amountViolation(
        negative = CreateSeasonViolation.NegativeTieFloor,
        tooLarge = CreateSeasonViolation.TieFloorTooLarge,
        tooManyDecimalPlaces = CreateSeasonViolation.TieFloorTooManyDecimalPlaces,
    )

private fun SeasonRules.sideTableViolation(): CreateSeasonViolation? =
    when {
        payouts.isEmpty() -> CreateSeasonViolation.NoPayoutPlaces
        sideBetRounds.isEmpty() -> CreateSeasonViolation.NoSideBetRounds
        sideBetRounds.toSet().size != sideBetRounds.size -> CreateSeasonViolation.DuplicateSideBetRound
        else ->
            payouts.amountViolation(
                negative = CreateSeasonViolation.NegativePayout,
                tooLarge = CreateSeasonViolation.PayoutTooLarge,
                tooManyDecimalPlaces = CreateSeasonViolation.PayoutTooManyDecimalPlaces,
            )
    }

private fun List<BigDecimal>.amountViolation(
    negative: CreateSeasonViolation.RuleAmount,
    tooLarge: CreateSeasonViolation.RuleAmount,
    tooManyDecimalPlaces: CreateSeasonViolation.RuleAmount,
): CreateSeasonViolation.RuleAmount? =
    when {
        any { it.signum() < 0 } -> negative
        any { it >= RULE_AMOUNT_OVERFLOW } -> tooLarge
        // The raw scale, not stripTrailingZeros': the driver sends it as the numeric's display scale, so a 0E-20000
        // fails the insert as surely as a 1E-20000.
        any { it.scale() > MONEY_SCALE } -> tooManyDecimalPlaces
        else -> null
    }
