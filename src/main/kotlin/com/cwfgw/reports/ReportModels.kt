@file:UseSerializers(BigDecimalSerializer::class)

package com.cwfgw.reports

import com.cwfgw.golfers.GolferId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.RoundScorecard
import com.cwfgw.tournaments.TournamentStatus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import java.math.BigDecimal

/*
 * BigDecimal serialization is wired at file scope (above) rather than per
 * property because [TeamRanking.series] is a `List<BigDecimal>` and kotlinx
 * can't apply `@Serializable(with = ...)` to a type argument cleanly.
 * Going file-wide keeps the property declarations uncluttered too.
 */

/** A type of its own, rather than two nullable fields on [ReportCell], so a tie without a position cannot be built. */
@Serializable
data class ReportCellFinish(
    val position: Int,
    val tied: Boolean,
)

/**
 * One cell in the weekly report grid — the intersection of a team
 * (column) and a draft round (row). Carries the picked golfer's
 * tournament result plus rolled-up season-to-date totals so the UI can
 * render the cell without a second lookup. Its nullable fields are null
 * where a team has an empty slot for a round, or the picked golfer has
 * no result yet (cut, withdrew, no-show).
 */
@Serializable
data class ReportCell(
    val round: Int,
    val golferName: String?,
    val golferId: GolferId?,
    val positionStr: String?,
    /**
     * Set exactly where [positionStr] is a finish label, which a season cell's top-ten count is not. Position
     * and tie state are read from here, never parsed back out of that label.
     */
    val finish: ReportCellFinish?,
    val earnings: BigDecimal,
    val ownershipPct: BigDecimal,
    val seasonEarnings: BigDecimal,
    val seasonTopTens: Int,
    /**
     * Set only by the live overlay, from the selected event's ESPN field; a persisted report is false throughout.
     * Says nothing about money: a cell pays only once its golfer has a round score.
     */
    val inField: Boolean,
)

/**
 * One column of the weekly-report grid: a single team's per-round cells
 * plus the summary totals the UI prints below the grid.
 */
@Serializable
data class ReportTeamColumn(
    val teamId: TeamId,
    val teamName: String,
    val cells: List<ReportCell>,
    val topTenEarnings: BigDecimal,
    val weeklyTotal: BigDecimal,
    val previous: BigDecimal,
    val subtotal: BigDecimal,
    val topTenCount: Int,
    val topTenMoney: BigDecimal,
    val sideBets: BigDecimal,
    val totalCash: BigDecimal,
)

/**
 * A golfer in the top-10 that no team rostered. Surfaced separately under
 * the report grid so viewers can see "you missed these names."
 */
@Serializable
data class UndraftedGolfer(
    val name: String,
    val payout: BigDecimal,
)

/** [teamNames] lists every team rostering the player, since a golfer can be split across teams; empty is undrafted. */
@Serializable
data class LeaderboardPlayer(
    val name: String,
    val teamNames: List<String>,
)

/**
 * One competitor slot, not one golfer: a team event's partners are a single row of two [players] sharing its
 * position, score and rounds. [position] and [scoreToPar] are null for a slot yet to tee off, listed after every
 * placed one. [rostered] is a body property so it cannot disagree with [players], and reaches the wire only because
 * `appJson` encodes defaults.
 */
@Serializable
data class LiveLeaderboardEntry(
    val position: Int?,
    val scoreToPar: Int?,
    val players: List<LeaderboardPlayer>,
    /** Per-round stroke totals (R1..R4) when ESPN ships them. Empty list if pre-tournament. */
    val roundScores: List<Int> = emptyList(),
    /** Sum of [roundScores] when at least one round has been played; null otherwise. */
    val totalStrokes: Int? = null,
    /** Live rows only: a stored report's are empty, as are those of a round ESPN sent without its holes. */
    val scorecards: List<RoundScorecard> = emptyList(),
) {
    val rostered: Boolean = players.any { it.teamNames.isNotEmpty() }
}

/**
 * Pure tags on the wire, so an `enum class` as [ReportScope] is. An entry the round has settled nothing
 * for carries null rather than a third variant here.
 */
@Serializable
enum class SideBetOutcome {
    /** At the round's highest cumulative, alone or tied: a round every team ties is won by all, at a zero payout. */
    @SerialName("won")
    Won,

    /** @see SideBetOutcome */
    @SerialName("lost")
    Lost,
}

/**
 * [outcome] is null where the round has settled nothing for this team, as when nobody has earned in it
 * yet. It is the server's decision, which [payout]'s sign cannot stand in for.
 */
@Serializable
data class ReportSideBetTeamEntry(
    val teamId: TeamId,
    val teamName: String,
    val golferName: String,
    val cumulativeEarnings: BigDecimal,
    val payout: BigDecimal,
    val outcome: SideBetOutcome?,
)

/** [teams] ranks best [ReportSideBetTeamEntry.cumulativeEarnings] first; a tie keeps [WeeklyReport.teams]' order. */
@Serializable
data class ReportSideBetRound(
    val round: Int,
    val teams: List<ReportSideBetTeamEntry>,
)

/** [rounds] lists the draft rounds in the order [WeeklyReport.sideBetDetail] stacks them. */
@Serializable
data class ReportSideBetRules(
    val amountPerTeam: BigDecimal,
    val rounds: List<Int>,
)

/** One row of the standings panel beside the report grid, ranked by [ReportTeamColumn.totalCash] descending. */
@Serializable
data class StandingsEntry(
    val rank: Int,
    val teamName: String,
)

/**
 * Tournament metadata block in a report. All fields nullable because the
 * season-aggregate report carries no specific tournament; the multiplier
 * defaults to 1.0 in that case.
 */
@Serializable
data class ReportTournamentInfo(
    val name: String?,
    val startDate: String?,
    val endDate: String?,
    val status: TournamentStatus?,
    val payoutMultiplier: BigDecimal,
    val week: String?,
)

/**
 * An `enum class` for the reason [TournamentStatus] gives: pure tags. Wire-only, so `@SerialName`
 * carries the value where that type needs a `value` property the repository can also read.
 */
@Serializable
enum class ReportScope {
    /** The "All Tournaments" roll-up, whose [WeeklyReport.tournament] block names no tournament. */
    @SerialName("season")
    Season,

    /** @see ReportScope */
    @SerialName("tournament")
    Tournament,
}

/**
 * [lost] is a sum of losses, so zero or negative, and [net] is [won] plus it. The constructor is private so
 * that [over] is the only way the server builds one; a decode bypasses it and keeps the three the JSON carried.
 */
@Serializable
@ConsistentCopyVisibility
data class WonLostTotals private constructor(
    val won: BigDecimal,
    val lost: BigDecimal,
    val net: BigDecimal,
) {
    companion object {
        /** A zero amount counts on neither side. */
        fun over(amounts: List<BigDecimal>): WonLostTotals {
            val won = amounts.filter { it.signum() > 0 }.fold(BigDecimal.ZERO, BigDecimal::add)
            val lost = amounts.filter { it.signum() < 0 }.fold(BigDecimal.ZERO, BigDecimal::add)
            return WonLostTotals(won = won, lost = lost, net = won.add(lost))
        }
    }
}

/**
 * [liveLeaderboard] is not live-only despite the name: a tournament report carries the persisted
 * leaderboard there, and a live overlay may replace it with ESPN's.
 *
 * [scope] has no default, so every construction site must say which report it built.
 *
 * [sideBetRules] is null for a season that stakes nothing on side bets, and [sideBetDetail] is then empty.
 * It has no default for the reason [scope] has none.
 *
 * [cashTotals], [sideBetTotals] and [weeklyTotals] are body properties so that `copy(teams = ...)`
 * recomputes them: as constructor parameters a live overlay would carry the base report's. They reach the
 * wire only because `appJson` encodes defaults.
 */
@Serializable
data class WeeklyReport(
    val scope: ReportScope,
    val tournament: ReportTournamentInfo,
    val teams: List<ReportTeamColumn>,
    val undraftedTopTens: List<UndraftedGolfer>,
    val sideBetRules: ReportSideBetRules?,
    val sideBetDetail: List<ReportSideBetRound>,
    val standingsOrder: List<StandingsEntry>,
    val live: Boolean? = null,
    val liveLeaderboard: List<LiveLeaderboardEntry> = emptyList(),
    /** @see Rankings.liveEligible */
    val liveEligible: Boolean = false,
    /** Set only when the live overlay matched an ESPN event nobody has teed off, listed field or not. */
    val awaitingTeeOff: Boolean = false,
    /**
     * No earlier tournament in the season is unfinalized, by finalize's own guard. False unless a builder asks, so a
     * report that never does offers no finalize; off the wire, where [finalizable] carries it.
     */
    @Transient val earlierFinalized: Boolean = false,
) {
    val cashTotals: WonLostTotals = WonLostTotals.over(teams.map(ReportTeamColumn::totalCash))
    val sideBetTotals: WonLostTotals = WonLostTotals.over(teams.map(ReportTeamColumn::sideBets))
    val weeklyTotals: WonLostTotals = WonLostTotals.over(teams.map(ReportTeamColumn::weeklyTotal))

    /**
     * A body property for the reasons the class doc gives the totals. Teams tied on [ReportTeamColumn.weeklyTotal]
     * keep [teams]' order rather than share a place, so a client numbering this list never skips a number.
     */
    val weeklyStandings: List<TeamId> =
        teams.sortedByDescending(ReportTeamColumn::weeklyTotal).map(ReportTeamColumn::teamId)

    /** @see weeklyStandings */
    val sideBetStandings: List<TeamId> =
        teams.sortedByDescending(ReportTeamColumn::sideBets).map(ReportTeamColumn::teamId)

    /** False while [awaitingTeeOff], which only a live read sets; the finalize endpoint itself does not refuse then. */
    val finalizable: Boolean =
        earlierFinalized && tournament.status != TournamentStatus.Completed && !awaitingTeeOff
}

/**
 * One team's entry in the rankings response, including the per-tournament
 * `series` so the UI can draw a cumulative-cash line chart without a
 * second round trip.
 */
@Serializable
data class TeamRanking(
    val teamId: TeamId,
    val teamName: String,
    val subtotal: BigDecimal,
    val sideBets: BigDecimal,
    val totalCash: BigDecimal,
    val series: List<BigDecimal>,
)

/** Full rankings response — teams with their standings, plus the column headers for the series chart. */
@Serializable
data class Rankings(
    /** Ranked by [TeamRanking.totalCash] descending; the UI numbers the rows in the order they arrive. */
    val teams: List<TeamRanking>,
    val weeks: List<String>,
    val tournamentNames: List<String>,
    val live: Boolean? = null,
    /**
     * Whether a `live=true` request for the same selection would ask ESPN: set from the candidate list the overlay
     * itself folds, so the UI's toggle cannot offer what the overlay would refuse. [live] says whether ESPN answered.
     */
    val liveEligible: Boolean = false,
) {
    /** A body property for the reason [WeeklyReport] gives its totals. */
    val cashTotals: WonLostTotals = WonLostTotals.over(teams.map(TeamRanking::totalCash))

    /**
     * One entry per [teams] entry, in its order. A list keyed by team id rather than a field on [TeamRanking], so it
     * is a body property for the reason [WeeklyReport] gives its totals.
     */
    val standings: List<TeamStanding> = TeamStanding.over(teams)
}

/**
 * Where a team stands on [TeamRanking.totalCash], ranked competition style: equal totals share a position and the
 * next total takes one plus the count above it (1, 2, 2, 4). Discriminated on the wire by `type`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TeamStanding {
    val teamId: TeamId
    val tied: Boolean

    /** First on its own or tied for it. [aheadOf] is null when no team has a lower total. */
    @Serializable
    @SerialName("leader")
    data class Leader(
        override val teamId: TeamId,
        override val tied: Boolean,
        val aheadOf: StandingGap?,
    ) : TeamStanding

    /** [behind] is the nearest strictly higher total, so it skips a team tied with this one. */
    @Serializable
    @SerialName("chaser")
    data class Chaser(
        override val teamId: TeamId,
        override val tied: Boolean,
        val position: Int,
        val behind: StandingGap,
        val behindLeader: BigDecimal,
    ) : TeamStanding

    companion object {
        fun over(teams: List<TeamRanking>): List<TeamStanding> {
            val totals = teams.map(TeamRanking::totalCash)
            val leaderTotal = totals.maxOrNull() ?: return emptyList()

            fun positionOf(total: BigDecimal): Int = 1 + totals.count { it > total }

            fun isTied(total: BigDecimal): Boolean = totals.count { it.compareTo(total) == 0 } > 1

            fun gapTo(otherTotal: BigDecimal, amount: BigDecimal): StandingGap =
                StandingGap(position = positionOf(otherTotal), tied = isTied(otherTotal), amount = amount)

            return teams.map { team ->
                val total = team.totalCash
                val nextHigher = totals.filter { it > total }.minOrNull()
                if (nextHigher == null) {
                    Leader(
                        teamId = team.teamId,
                        tied = isTied(total),
                        aheadOf =
                            totals.filter { it < total }.maxOrNull()?.let { lower ->
                                gapTo(lower, total - lower)
                            },
                    )
                } else {
                    Chaser(
                        teamId = team.teamId,
                        tied = isTied(total),
                        position = positionOf(total),
                        behind = gapTo(nextHigher, nextHigher - total),
                        behindLeader = leaderTotal - total,
                    )
                }
            }
        }
    }
}

/** [amount] is never negative: the distance between the two totals, whichever side this team is on. */
@Serializable
data class StandingGap(
    val position: Int,
    val tied: Boolean,
    val amount: BigDecimal,
)

/** One top-10 finish in a golfer's season history. */
@Serializable
data class GolferHistoryEntry(
    val tournament: String,
    val position: Int,
    val earnings: BigDecimal,
)

/** A golfer's season history — every top-10 finish plus season totals. */
@Serializable
data class GolferHistory(
    val golferName: String,
    val totalEarnings: BigDecimal,
    val topTens: Int,
    val results: List<GolferHistoryEntry>,
)
