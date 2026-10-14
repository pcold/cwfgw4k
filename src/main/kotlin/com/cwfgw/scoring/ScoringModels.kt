package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.toUUIDOrNull
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@JvmInline
value class FantasyScoreId(
    val value: UUID,
)

@JvmInline
value class SeasonStandingId(
    val value: UUID,
)

fun String.toFantasyScoreId(): FantasyScoreId? = toUUIDOrNull()?.let(::FantasyScoreId)

fun String.toSeasonStandingId(): SeasonStandingId? = toUUIDOrNull()?.let(::SeasonStandingId)

/**
 * Persisted record of a golfer's payout for one team in one tournament. The
 * tuple `(season_id, team_id, tournament_id, golfer_id)` is unique so a
 * recalculation upserts in place rather than appending.
 */
data class FantasyScore(
    val id: FantasyScoreId,
    val seasonId: SeasonId,
    val teamId: TeamId,
    val tournamentId: TournamentId,
    val golferId: GolferId,
    val points: BigDecimal,
    val position: Int,
    val numTied: Int,
    val basePayout: BigDecimal,
    val ownershipPct: BigDecimal,
    val payout: BigDecimal,
    val multiplier: BigDecimal,
    val calculatedAt: Instant,
)

/** Persisted aggregate of a team's season-long earnings, refreshed after scoring. */
data class SeasonStanding(
    val id: SeasonStandingId,
    val seasonId: SeasonId,
    val teamId: TeamId,
    val totalPoints: BigDecimal,
    val tournamentsPlayed: Int,
    val lastUpdated: Instant,
)

/** How a single golfer's payout was derived. Persisted alongside [FantasyScore]. */
data class ScoreBreakdown(
    val position: Int,
    val numTied: Int,
    val basePayout: BigDecimal,
    val ownershipPct: BigDecimal,
    val payout: BigDecimal,
    val multiplier: BigDecimal,
)

/** Transient: a single golfer's contribution to a team's weekly result. */
data class GolferScoreEntry(
    val golferId: GolferId,
    val payout: BigDecimal,
    val breakdown: ScoreBreakdown,
)

/**
 * Transient: one team's weekly result. `weeklyTotal` is the zero-sum
 * redistribution: `topTens * numTeams - totalPot`, so the sum across all
 * teams is always zero.
 */
data class TeamWeeklyResult(
    val teamId: TeamId,
    val teamName: String,
    val topTens: BigDecimal,
    val weeklyTotal: BigDecimal,
    val golferScores: List<GolferScoreEntry>,
)

/** Transient: the full result of scoring a tournament. */
data class WeeklyScoreResult(
    val tournamentId: TournamentId,
    val multiplier: BigDecimal,
    val numTeams: Int,
    val totalPot: BigDecimal,
    val teams: List<TeamWeeklyResult>,
)
