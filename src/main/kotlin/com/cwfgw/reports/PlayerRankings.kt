package com.cwfgw.reports

import com.cwfgw.golfers.GolferId
import com.cwfgw.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `topTens` and earnings count each (team, finish) pair, so a golfer two teams roster who top-10s once
 * contributes 2; `eventsPlayed` counts tournaments and never multiplies. Undrafted rows have
 * `golferId = null` and `key = "u:<name>"`, the name being the report builder's display string.
 */
@Serializable
data class PlayerRankingsRow(
    val key: String,
    val golferId: GolferId? = null,
    val name: String,
    val topTens: Int,
    val eventsPlayed: Int,
    @Serializable(with = BigDecimalSerializer::class) val totalEarnings: BigDecimal,
    val teamName: String? = null,
    val draftRound: Int? = null,
)

/**
 * Server-rendered player rankings: the list of [PlayerRankingsRow]
 * sorted by total earnings (descending), top-tens (descending), then
 * name (ascending). Replaces the old UI flow where the page issued one
 * `/report/<tid>` query per tournament and aggregated client-side —
 * that fan-out exhausted the DB pool under load.
 */
@Serializable
data class PlayerRankings(
    val players: List<PlayerRankingsRow>,
    val live: Boolean,
    /** @see Rankings.liveEligible */
    val liveEligible: Boolean,
)

/**
 * Immutable so the live overlay returns a new instance rather than mutating the base. `undrafted` is
 * keyed by display name because a live leaderboard competitor may match no golfer, and the persisted
 * path uses the same key shape so the two combine without a join.
 */
internal data class PlayerRankingsAcc(
    val drafted: Map<GolferId, DraftedAgg>,
    val undrafted: Map<String, UndraftedAgg>,
    /** Every golfer with a result or a live leaderboard entry, top 10 or not: a row reads its count here. */
    val eventsPlayed: Map<GolferId, Int> = emptyMap(),
    /** Set by folding in a preview, never from the request's flag. */
    val live: Boolean = false,
)

internal data class DraftedAgg(
    val topTens: Int,
    val totalEarnings: BigDecimal,
)

/**
 * [golferIds] are the golfers behind the name, whose counts in [PlayerRankingsAcc.eventsPlayed] the row sums.
 * [unlinkedEvents] counts live events where the competitor matched no golfer, so there is no id to count by.
 */
internal data class UndraftedAgg(
    val topTens: Int,
    val totalEarnings: BigDecimal,
    val golferIds: Set<GolferId> = emptySet(),
    val unlinkedEvents: Int = 0,
)
