package com.cwfgw.reports

import com.cwfgw.golfers.GolferId
import com.cwfgw.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * One row of the Player Rankings table — either a drafted golfer (with
 * roster context) or an undrafted top-10 finisher aggregated by name.
 *
 * Earnings is the sum of ownership-adjusted payouts across every (team
 * that rostered them, top-10 finish) pair, matching the behavior the
 * UI previously assembled by walking N per-tournament reports.
 * `topTens` likewise counts each (team, finish) pair so a golfer
 * rostered by two teams who top-10s once contributes 2.
 *
 * Undrafted rows have `golferId = null` and `key = "u:<name>"`. The
 * name is the display string the report builder produces ("F. Lastname"
 * for non-team events).
 */
@Serializable
data class PlayerRankingsRow(
    val key: String,
    val golferId: GolferId? = null,
    val name: String,
    val topTens: Int,
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
    val live: Boolean? = null,
)

/**
 * Working accumulator for the player-rankings build. Kept immutable so
 * the live-overlay pass returns a new instance rather than mutating the
 * base — the same data-flow shape the team rankings overlay uses.
 *
 * `drafted` is keyed by golferId because we always have a golferId for
 * persisted FantasyScores and ESPN preview entries that match a roster.
 * `undrafted` is keyed by display name because ESPN's leaderboard
 * doesn't give us a golferId for unrostered competitors and the
 * persisted-results path has to use the same key shape so the two
 * combine without a join.
 */
internal data class PlayerRankingsAcc(
    val drafted: Map<GolferId, DraftedAgg>,
    val undrafted: Map<String, UndraftedAgg>,
)

internal data class DraftedAgg(
    val topTens: Int,
    val totalEarnings: BigDecimal,
)

internal data class UndraftedAgg(
    val topTens: Int,
    val totalEarnings: BigDecimal,
)
