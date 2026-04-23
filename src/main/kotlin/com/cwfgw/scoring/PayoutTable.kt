package com.cwfgw.scoring

import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.TeamId
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure payout math for the league's top-N fantasy scoring. Stays free of any
 * IO so its properties (zero-sum, ownership-splits-sum-to-base, tie-floor)
 * are easy to assert in property-based tests.
 */
internal object PayoutTable {
    private const val OWNERSHIP_DENOMINATOR = 100
    private const val SPLIT_SCALE = 4

    /**
     * Tie-split payout for a tournament position. T4 with three tied golfers
     * and the default payout table averages positions 4–6 ($8+$7+$6)/3 = $7,
     * then applies the multiplier. A tie that overlaps the payout zone is
     * floored at [SeasonRules.tieFloor] per player so the cheap end of the
     * board never pays less than the floor.
     */
    fun tieSplitPayout(
        position: Int,
        numTied: Int,
        multiplier: BigDecimal,
        rules: SeasonRules,
    ): BigDecimal {
        val payouts = rules.payouts
        val numPlaces = payouts.size
        if (position > numPlaces) return BigDecimal.ZERO
        if (numTied <= 1) {
            return payouts.getOrNull(position - 1)?.multiply(multiplier) ?: BigDecimal.ZERO
        }
        val totalPayout =
            (position until position + numTied)
                .mapNotNull { p -> payouts.getOrNull(p - 1) }
                .fold(BigDecimal.ZERO, BigDecimal::add)
        val averaged = totalPayout.divide(BigDecimal(numTied), SPLIT_SCALE, RoundingMode.HALF_UP)
        return averaged.max(rules.tieFloor).multiply(multiplier)
    }

    /**
     * Split a base payout across owners so the rounded shares sum exactly to
     * [basePayout]. The largest-ownership share is rounded first; the last
     * owner absorbs the remainder. With a single owner the full amount goes
     * to them — no rounding loss possible.
     */
    fun splitOwnership(
        basePayout: BigDecimal,
        owners: List<Pair<TeamId, BigDecimal>>,
    ): Map<TeamId, BigDecimal> {
        if (owners.size <= 1) {
            return owners.associate { (teamId, _) -> teamId to basePayout }
        }
        val sorted = owners.sortedByDescending { it.second }
        val rounded =
            sorted.dropLast(1).map { (teamId, pct) ->
                teamId to
                    basePayout.multiply(pct)
                        .divide(BigDecimal(OWNERSHIP_DENOMINATOR), SPLIT_SCALE, RoundingMode.HALF_UP)
            }
        val remainder = basePayout.subtract(rounded.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc.add(amount) })
        return (rounded + (sorted.last().first to remainder)).toMap()
    }
}
