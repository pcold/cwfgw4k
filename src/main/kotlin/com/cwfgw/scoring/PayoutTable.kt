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
     *
     * For team events (Zurich Classic), two ESPN partner rows share one
     * leaderboard position. We halve the per-player payout so the team
     * collects the same total as a single non-team finisher would, and we
     * count tied units as `numTied / 2` because a "tie" between two team
     * entries is really one team-position, not two.
     */
    fun tieSplitPayout(
        position: Int,
        numTied: Int,
        multiplier: BigDecimal,
        rules: SeasonRules,
        isTeamEvent: Boolean = false,
    ): BigDecimal {
        val payouts = rules.payouts
        val numPlaces = payouts.size
        if (position > numPlaces) return BigDecimal.ZERO
        val tiedUnits = if (isTeamEvent) maxOf(1, numTied / 2) else numTied
        val perTeamPayout =
            if (tiedUnits <= 1) {
                payouts.getOrNull(position - 1)?.multiply(multiplier) ?: BigDecimal.ZERO
            } else {
                val totalPayout =
                    (position until position + tiedUnits)
                        .mapNotNull { p -> payouts.getOrNull(p - 1) }
                        .fold(BigDecimal.ZERO, BigDecimal::add)
                val averaged = totalPayout.divide(BigDecimal(tiedUnits), SPLIT_SCALE, RoundingMode.HALF_UP)
                averaged.max(rules.tieFloor).multiply(multiplier)
            }
        return if (isTeamEvent) {
            perTeamPayout.divide(TEAM_EVENT_SHARE, SPLIT_SCALE, RoundingMode.HALF_UP)
        } else {
            perTeamPayout
        }
    }

    private val TEAM_EVENT_SHARE: BigDecimal = BigDecimal(2)

    /**
     * Split a base payout across owners so the rounded shares sum exactly to
     * [basePayout]. The largest-ownership share is rounded first; the last
     * owner absorbs the remainder. With a single owner the full amount goes
     * to them — no rounding loss possible.
     *
     * Every non-zero recipient share is then bumped up to [floor] if it would
     * otherwise fall below — this is the league's "minimum payout for any
     * bet or split" rule. Zero stays zero (positions outside the payout zone
     * shouldn't suddenly start paying). Bumping is intentional: total league
     * outflow can exceed [basePayout] — the floor is a guarantee, not a
     * redistribution.
     */
    fun splitOwnership(
        basePayout: BigDecimal,
        owners: List<Pair<TeamId, BigDecimal>>,
        floor: BigDecimal,
    ): Map<TeamId, BigDecimal> {
        if (owners.size <= 1) {
            return owners.associate { (teamId, _) -> teamId to applyFloor(basePayout, floor) }
        }
        val sorted = owners.sortedByDescending { it.second }
        val rounded =
            sorted.dropLast(1).map { (teamId, pct) ->
                teamId to
                    basePayout.multiply(pct)
                        .divide(BigDecimal(OWNERSHIP_DENOMINATOR), SPLIT_SCALE, RoundingMode.HALF_UP)
            }
        val remainder = basePayout.subtract(rounded.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc.add(amount) })
        val withRemainder = rounded + (sorted.last().first to remainder)
        return withRemainder.associate { (teamId, share) -> teamId to applyFloor(share, floor) }
    }

    private fun applyFloor(
        amount: BigDecimal,
        floor: BigDecimal,
    ): BigDecimal = if (amount.signum() > 0 && amount < floor) floor else amount
}
