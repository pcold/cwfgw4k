package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.FantasyScoresRecord
import com.cwfgw.jooq.tables.records.SeasonStandingsRecord
import com.cwfgw.jooq.tables.references.FANTASY_SCORES
import com.cwfgw.jooq.tables.references.SEASON_STANDINGS
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import java.math.BigDecimal

interface ScoringRepository {
    suspend fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore>

    suspend fun getStandings(seasonId: SeasonId): List<SeasonStanding>

    suspend fun upsertScore(record: UpsertScore): FantasyScore

    suspend fun golferPointTotal(
        seasonId: SeasonId,
        teamId: TeamId,
        golferId: GolferId,
    ): BigDecimal

    suspend fun teamSeasonTotals(
        seasonId: SeasonId,
        teamId: TeamId,
    ): TeamSeasonTotals

    suspend fun upsertStanding(
        seasonId: SeasonId,
        teamId: TeamId,
        totalPoints: BigDecimal,
        tournamentsPlayed: Int,
    ): SeasonStanding
}

data class TeamSeasonTotals(
    val totalPoints: BigDecimal,
    val tournamentsPlayed: Int,
)

/**
 * Inputs to upsert one golfer's score for a team in a tournament. The
 * caller already computed `breakdown.payout` as the persisted points value;
 * a separate [UpsertScore.points] field would be redundant.
 */
data class UpsertScore(
    val seasonId: SeasonId,
    val teamId: TeamId,
    val tournamentId: TournamentId,
    val golferId: GolferId,
    val breakdown: ScoreBreakdown,
)

fun ScoringRepository(dsl: DSLContext): ScoringRepository = JooqScoringRepository(dsl)

private class JooqScoringRepository(private val dsl: DSLContext) : ScoringRepository {
    override suspend fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(FANTASY_SCORES)
                .where(FANTASY_SCORES.SEASON_ID.eq(seasonId.value))
                .and(FANTASY_SCORES.TOURNAMENT_ID.eq(tournamentId.value))
                .orderBy(FANTASY_SCORES.POINTS.desc())
                .fetch(::toFantasyScore)
        }

    override suspend fun getStandings(seasonId: SeasonId): List<SeasonStanding> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(SEASON_STANDINGS)
                .where(SEASON_STANDINGS.SEASON_ID.eq(seasonId.value))
                .orderBy(SEASON_STANDINGS.TOTAL_POINTS.desc())
                .fetch(::toSeasonStanding)
        }

    override suspend fun upsertScore(record: UpsertScore): FantasyScore =
        withContext(Dispatchers.IO) {
            val upserted =
                dsl.insertInto(FANTASY_SCORES)
                    .set(scoreInsertAssignments(record))
                    .onConflict(
                        FANTASY_SCORES.SEASON_ID,
                        FANTASY_SCORES.TEAM_ID,
                        FANTASY_SCORES.TOURNAMENT_ID,
                        FANTASY_SCORES.GOLFER_ID,
                    )
                    .doUpdate()
                    .set(scoreUpdateAssignments(record.breakdown))
                    .returning()
                    .fetchOne() ?: error("UPSERT RETURNING produced no row for fantasy_scores")
            toFantasyScore(upserted)
        }

    override suspend fun golferPointTotal(
        seasonId: SeasonId,
        teamId: TeamId,
        golferId: GolferId,
    ): BigDecimal =
        withContext(Dispatchers.IO) {
            dsl.select(DSL.coalesce(DSL.sum(FANTASY_SCORES.POINTS), BigDecimal.ZERO))
                .from(FANTASY_SCORES)
                .where(FANTASY_SCORES.SEASON_ID.eq(seasonId.value))
                .and(FANTASY_SCORES.TEAM_ID.eq(teamId.value))
                .and(FANTASY_SCORES.GOLFER_ID.eq(golferId.value))
                .fetchOne(0, BigDecimal::class.java) ?: BigDecimal.ZERO
        }

    override suspend fun teamSeasonTotals(
        seasonId: SeasonId,
        teamId: TeamId,
    ): TeamSeasonTotals =
        withContext(Dispatchers.IO) {
            val totalField = DSL.coalesce(DSL.sum(FANTASY_SCORES.POINTS), BigDecimal.ZERO)
            val countField = DSL.countDistinct(FANTASY_SCORES.TOURNAMENT_ID)
            dsl.select(totalField, countField)
                .from(FANTASY_SCORES)
                .where(FANTASY_SCORES.SEASON_ID.eq(seasonId.value))
                .and(FANTASY_SCORES.TEAM_ID.eq(teamId.value))
                .fetchOne { record ->
                    TeamSeasonTotals(
                        totalPoints = record.value1() ?: BigDecimal.ZERO,
                        tournamentsPlayed = record.value2() ?: 0,
                    )
                } ?: TeamSeasonTotals(BigDecimal.ZERO, 0)
        }

    override suspend fun upsertStanding(
        seasonId: SeasonId,
        teamId: TeamId,
        totalPoints: BigDecimal,
        tournamentsPlayed: Int,
    ): SeasonStanding =
        withContext(Dispatchers.IO) {
            val insertValues =
                mapOf<Field<*>, Any?>(
                    SEASON_STANDINGS.SEASON_ID to seasonId.value,
                    SEASON_STANDINGS.TEAM_ID to teamId.value,
                    SEASON_STANDINGS.TOTAL_POINTS to totalPoints,
                    SEASON_STANDINGS.TOURNAMENTS_PLAYED to tournamentsPlayed,
                )
            val updateValues =
                mapOf<Field<*>, Any?>(
                    SEASON_STANDINGS.TOTAL_POINTS to totalPoints,
                    SEASON_STANDINGS.TOURNAMENTS_PLAYED to tournamentsPlayed,
                    SEASON_STANDINGS.LAST_UPDATED to DSL.currentOffsetDateTime(),
                )
            val upserted =
                dsl.insertInto(SEASON_STANDINGS)
                    .set(insertValues)
                    .onConflict(SEASON_STANDINGS.SEASON_ID, SEASON_STANDINGS.TEAM_ID)
                    .doUpdate()
                    .set(updateValues)
                    .returning()
                    .fetchOne() ?: error("UPSERT RETURNING produced no row for season_standings")
            toSeasonStanding(upserted)
        }

    private fun scoreInsertAssignments(record: UpsertScore): Map<Field<*>, Any?> =
        mapOf(
            FANTASY_SCORES.SEASON_ID to record.seasonId.value,
            FANTASY_SCORES.TEAM_ID to record.teamId.value,
            FANTASY_SCORES.TOURNAMENT_ID to record.tournamentId.value,
            FANTASY_SCORES.GOLFER_ID to record.golferId.value,
            FANTASY_SCORES.POINTS to record.breakdown.payout,
            FANTASY_SCORES.POSITION to record.breakdown.position,
            FANTASY_SCORES.NUM_TIED to record.breakdown.numTied,
            FANTASY_SCORES.BASE_PAYOUT to record.breakdown.basePayout,
            FANTASY_SCORES.OWNERSHIP_PCT to record.breakdown.ownershipPct,
            FANTASY_SCORES.PAYOUT to record.breakdown.payout,
            FANTASY_SCORES.MULTIPLIER to record.breakdown.multiplier,
        )

    private fun scoreUpdateAssignments(breakdown: ScoreBreakdown): Map<Field<*>, Any?> =
        mapOf(
            FANTASY_SCORES.POINTS to breakdown.payout,
            FANTASY_SCORES.POSITION to breakdown.position,
            FANTASY_SCORES.NUM_TIED to breakdown.numTied,
            FANTASY_SCORES.BASE_PAYOUT to breakdown.basePayout,
            FANTASY_SCORES.OWNERSHIP_PCT to breakdown.ownershipPct,
            FANTASY_SCORES.PAYOUT to breakdown.payout,
            FANTASY_SCORES.MULTIPLIER to breakdown.multiplier,
            FANTASY_SCORES.CALCULATED_AT to DSL.currentOffsetDateTime(),
        )

    private fun toFantasyScore(record: FantasyScoresRecord): FantasyScore =
        FantasyScore(
            id = FantasyScoreId(checkNotNull(record.id) { "fantasy_scores.id is NOT NULL but returned null" }),
            seasonId = SeasonId(record.seasonId),
            teamId = TeamId(record.teamId),
            tournamentId = TournamentId(record.tournamentId),
            golferId = GolferId(record.golferId),
            points = checkNotNull(record.points) { "fantasy_scores.points is NOT NULL but returned null" },
            position = checkNotNull(record.position) { "fantasy_scores.position is NOT NULL but returned null" },
            numTied = checkNotNull(record.numTied) { "fantasy_scores.num_tied is NOT NULL but returned null" },
            basePayout =
                checkNotNull(record.basePayout) {
                    "fantasy_scores.base_payout is NOT NULL but returned null"
                },
            ownershipPct =
                checkNotNull(record.ownershipPct) {
                    "fantasy_scores.ownership_pct is NOT NULL but returned null"
                },
            payout = checkNotNull(record.payout) { "fantasy_scores.payout is NOT NULL but returned null" },
            multiplier = checkNotNull(record.multiplier) { "fantasy_scores.multiplier is NOT NULL but returned null" },
            calculatedAt =
                checkNotNull(record.calculatedAt) {
                    "fantasy_scores.calculated_at is NOT NULL but returned null"
                }.toInstant(),
        )

    private fun toSeasonStanding(record: SeasonStandingsRecord): SeasonStanding =
        SeasonStanding(
            id = SeasonStandingId(checkNotNull(record.id) { "season_standings.id is NOT NULL but returned null" }),
            seasonId = SeasonId(record.seasonId),
            teamId = TeamId(record.teamId),
            totalPoints =
                checkNotNull(record.totalPoints) {
                    "season_standings.total_points is NOT NULL but returned null"
                },
            tournamentsPlayed =
                checkNotNull(record.tournamentsPlayed) {
                    "season_standings.tournaments_played is NOT NULL but returned null"
                },
            lastUpdated =
                checkNotNull(record.lastUpdated) {
                    "season_standings.last_updated is NOT NULL but returned null"
                }.toInstant(),
        )
}
