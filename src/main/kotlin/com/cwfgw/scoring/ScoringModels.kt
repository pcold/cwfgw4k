package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class FantasyScoreId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

@Serializable
@JvmInline
value class SeasonStandingId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun String.toFantasyScoreId(): FantasyScoreId? = toUUIDOrNull()?.let(::FantasyScoreId)

fun String.toSeasonStandingId(): SeasonStandingId? = toUUIDOrNull()?.let(::SeasonStandingId)

/**
 * Persisted record of a golfer's payout for one team in one tournament. The
 * tuple `(season_id, team_id, tournament_id, golfer_id)` is unique so a
 * recalculation upserts in place rather than appending.
 */
@Serializable
data class FantasyScore(
    val id: FantasyScoreId,
    val seasonId: SeasonId,
    val teamId: TeamId,
    val tournamentId: TournamentId,
    val golferId: GolferId,
    @Serializable(with = BigDecimalSerializer::class) val points: BigDecimal,
    val position: Int,
    val numTied: Int,
    @Serializable(with = BigDecimalSerializer::class) val basePayout: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val ownershipPct: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val payout: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val multiplier: BigDecimal,
    @Serializable(with = InstantSerializer::class) val calculatedAt: Instant,
)

/** Persisted aggregate of a team's season-long earnings, refreshed after scoring. */
@Serializable
data class SeasonStanding(
    val id: SeasonStandingId,
    val seasonId: SeasonId,
    val teamId: TeamId,
    @Serializable(with = BigDecimalSerializer::class) val totalPoints: BigDecimal,
    val tournamentsPlayed: Int,
    @Serializable(with = InstantSerializer::class) val lastUpdated: Instant,
)

/** How a single golfer's payout was derived. Persisted alongside [FantasyScore]. */
@Serializable
data class ScoreBreakdown(
    val position: Int,
    val numTied: Int,
    @Serializable(with = BigDecimalSerializer::class) val basePayout: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val ownershipPct: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val payout: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val multiplier: BigDecimal,
)

/** Transient: a single golfer's contribution to a team's weekly result. */
@Serializable
data class GolferScoreEntry(
    val golferId: GolferId,
    @Serializable(with = BigDecimalSerializer::class) val payout: BigDecimal,
    val breakdown: ScoreBreakdown,
)

/**
 * Transient: one team's weekly result. `weeklyTotal` is the zero-sum
 * redistribution: `topTens * numTeams - totalPot`, so the sum across all
 * teams is always zero.
 */
@Serializable
data class TeamWeeklyResult(
    val teamId: TeamId,
    val teamName: String,
    @Serializable(with = BigDecimalSerializer::class) val topTens: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val weeklyTotal: BigDecimal,
    val golferScores: List<GolferScoreEntry>,
)

/** Transient: the full result of scoring a tournament. */
@Serializable
data class WeeklyScoreResult(
    val tournamentId: TournamentId,
    @Serializable(with = BigDecimalSerializer::class) val multiplier: BigDecimal,
    val numTeams: Int,
    @Serializable(with = BigDecimalSerializer::class) val totalPot: BigDecimal,
    val teams: List<TeamWeeklyResult>,
)

/** A team's row inside one side-bet round. */
@Serializable
data class SideBetEntry(
    val teamId: TeamId,
    val teamName: String,
    val golferId: GolferId,
    @Serializable(with = BigDecimalSerializer::class) val cumulativeEarnings: BigDecimal,
)

/** The leading team for one side-bet round, when one exists. */
@Serializable
data class SideBetWinner(
    val teamId: TeamId,
    val teamName: String,
    val golferId: GolferId,
    @Serializable(with = BigDecimalSerializer::class) val cumulativeEarnings: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val netWinnings: BigDecimal,
)

/**
 * One draft round's side-bet standings. `active = false` when no team's pick
 * has earned anything yet — there is no winner and no P&L.
 */
@Serializable
data class SideBetRound(
    val round: Int,
    val active: Boolean,
    val winner: SideBetWinner?,
    val entries: List<SideBetEntry>,
)

/** Aggregate side-bet P&L for a team across every active round. */
@Serializable
data class SideBetTeamTotal(
    val teamId: TeamId,
    val teamName: String,
    val wins: Int,
    @Serializable(with = BigDecimalSerializer::class) val net: BigDecimal,
)

/** Full season-long side-bet standings: per-round detail plus per-team totals. */
@Serializable
data class SideBetStandings(
    val rounds: List<SideBetRound>,
    val teamTotals: List<SideBetTeamTotal>,
)
