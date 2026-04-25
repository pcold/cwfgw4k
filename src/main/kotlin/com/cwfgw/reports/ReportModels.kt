@file:UseSerializers(BigDecimalSerializer::class)

package com.cwfgw.reports

import com.cwfgw.golfers.GolferId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
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
)

/** One team's row inside a side-bet round detail (cumulative earnings + that round's payout). */
@Serializable
data class ReportSideBetTeamEntry(
    val teamId: TeamId,
    val golferName: String,
    val cumulativeEarnings: BigDecimal,
    val payout: BigDecimal,
)

/** All teams' side-bet entries for one draft round; the report stacks one of these per round. */
@Serializable
data class ReportSideBetRound(
    val round: Int,
    val teams: List<ReportSideBetTeamEntry>,
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
 * The full weekly or season report payload. `live` and `liveLeaderboard`
 * are set only for live overlays; non-live reports leave them at their
 * defaults so the wire shape stays compact.
 */
@Serializable
data class WeeklyReport(
    val tournament: ReportTournamentInfo,
    val teams: List<ReportTeamColumn>,
    val undraftedTopTens: List<UndraftedGolfer>,
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
