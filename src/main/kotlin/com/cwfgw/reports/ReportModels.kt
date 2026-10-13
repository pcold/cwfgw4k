@file:UseSerializers(BigDecimalSerializer::class)

package com.cwfgw.reports

import com.cwfgw.golfers.GolferId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal

/*
 * BigDecimal serialization is wired at file scope (above) rather than per
 * property because [TeamRanking.series] is a `List<BigDecimal>` and kotlinx
 * can't apply `@Serializable(with = ...)` to a type argument cleanly.
 * Going file-wide keeps the property declarations uncluttered too.
 */

/**
 * One cell in the weekly report grid — the intersection of a team
 * (column) and a draft round (row). Carries the picked golfer's
 * tournament result plus rolled-up season-to-date totals so the UI can
 * render the cell without a second lookup. Most fields are nullable
 * because a team may have an empty slot for a round, or the picked
 * golfer may have no result yet (cut, withdrew, no-show). `pairKey` is
 * set only for team events (e.g. Zurich Classic), where two partners
 * share a synthetic team id and need to be regrouped on the UI.
 */
@Serializable
data class ReportCell(
    val round: Int,
    val golferName: String?,
    val golferId: GolferId?,
    val positionStr: String?,
    val scoreToPar: String?,
    val earnings: BigDecimal,
    val topTens: Int,
    val ownershipPct: BigDecimal,
    val seasonEarnings: BigDecimal,
    val seasonTopTens: Int,
    val pairKey: String? = null,
)

/**
 * One column of the weekly-report grid: a single team's per-round cells
 * plus the summary totals the UI prints below the grid.
 */
@Serializable
data class ReportTeamColumn(
    val teamId: TeamId,
    val teamName: String,
    val ownerName: String,
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
 * the report grid so viewers can see "you missed these names." `pairKey`
 * mirrors the team-event pairing logic from [ReportCell].
 */
@Serializable
data class UndraftedGolfer(
    val name: String,
    val position: Int?,
    val payout: BigDecimal,
    val scoreToPar: String? = null,
    val pairKey: String? = null,
)

/**
 * One leaderboard row from ESPN, populated only when a report is rendered
 * with `live=true`. The React scoreboard renders the full top-20 directly
 * from this list so it can mix rostered and undrafted players in the
 * correct order without having to merge two separate lists client-side.
 */
@Serializable
data class LiveLeaderboardEntry(
    val name: String,
    val position: Int,
    val scoreToPar: String?,
    val rostered: Boolean,
    val teamName: String?,
    val pairKey: String? = null,
    /** Per-round stroke totals (R1..R4) when ESPN ships them. Empty list if pre-tournament. */
    val roundScores: List<Int> = emptyList(),
    /** Sum of [roundScores] when at least one round has been played; null otherwise. */
    val totalStrokes: Int? = null,
)

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

/** All teams' side-bet entries for one draft round; the report stacks one of these per round. */
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

/** One row of the standings panel beside the report grid (rank ordered by totalCash desc). */
@Serializable
data class StandingsEntry(
    val rank: Int,
    val teamName: String,
    val totalCash: BigDecimal,
)

/**
 * Tournament metadata block in a report. All fields nullable because the
 * season-aggregate report carries no specific tournament; the multiplier
 * defaults to 1.0 in that case.
 */
@Serializable
data class ReportTournamentInfo(
    val id: TournamentId?,
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
 * [liveLeaderboard] is not live-only despite the name: a tournament report carries the persisted
 * leaderboard there, and a live overlay may replace it with ESPN's.
 *
 * [scope] has no default, so every construction site must say which report it built.
 *
 * [sideBetRules] is null for a season that stakes nothing on side bets, and [sideBetDetail] is then empty.
 * It has no default for the reason [scope] has none.
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
)

/**
 * One team's entry in the rankings response, including the per-tournament
 * `series` so the UI can draw a cumulative-cash line chart without a
 * second round trip. `liveWeekly` is set only when `live=true` to surface
 * a projected current-week delta beside the locked-in series.
 */
@Serializable
data class TeamRanking(
    val teamId: TeamId,
    val teamName: String,
    val subtotal: BigDecimal,
    val sideBets: BigDecimal,
    val totalCash: BigDecimal,
    val series: List<BigDecimal>,
    val liveWeekly: BigDecimal? = null,
)

/** Full rankings response — teams plus the column headers for the series chart. */
@Serializable
data class Rankings(
    val teams: List<TeamRanking>,
    val weeks: List<String>,
    val tournamentNames: List<String>,
    val live: Boolean? = null,
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
    val golferId: GolferId,
    val totalEarnings: BigDecimal,
    val topTens: Int,
    val results: List<GolferHistoryEntry>,
)
