package com.cwfgw.scoring

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.required
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.FantasyScoresRecord
import com.cwfgw.jooq.tables.references.FANTASY_SCORES
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import org.jooq.Field
import org.jooq.impl.DSL

interface ScoringRepository {
    context(ctx: TransactionContext)
    fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore>

    /**
     * Single-query alternative to `tournaments.flatMap { getScores(seasonId, it.id) }`.
     * Returns every score for the season; when [tournamentIds] is non-null, restricts
     * to scores whose `tournament_id` is in that set. An empty [tournamentIds] returns
     * an empty list (matches the flatMap semantics — flatMap over an empty list is
     * empty, not "all tournaments"). Ordered by `(tournament_id, points desc)` so
     * `groupBy { it.tournamentId }` at the call site preserves per-tournament order.
     */
    context(ctx: TransactionContext)
    fun getScoresBySeason(
        seasonId: SeasonId,
        tournamentIds: Collection<TournamentId>? = null,
    ): List<FantasyScore>

    context(ctx: TransactionContext)
    fun upsertScore(record: UpsertScore): FantasyScore

    /** Wipe every fantasy score for the given tournament. Returns the row count for logging. */
    context(ctx: TransactionContext)
    fun deleteByTournament(tournamentId: TournamentId): Int

    /** Wipe every fantasy score for the season across all tournaments. */
    context(ctx: TransactionContext)
    fun deleteBySeason(seasonId: SeasonId): Int
}

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

fun ScoringRepository(): ScoringRepository = JooqScoringRepository()

private class JooqScoringRepository : ScoringRepository {
    context(ctx: TransactionContext)
    override fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> =
        ctx.dsl.selectFrom(FANTASY_SCORES)
            .where(FANTASY_SCORES.SEASON_ID.eq(seasonId.value))
            .and(FANTASY_SCORES.TOURNAMENT_ID.eq(tournamentId.value))
            .orderBy(FANTASY_SCORES.POINTS.desc())
            .fetch(::toFantasyScore)

    context(ctx: TransactionContext)
    override fun getScoresBySeason(
        seasonId: SeasonId,
        tournamentIds: Collection<TournamentId>?,
    ): List<FantasyScore> {
        if (tournamentIds != null && tournamentIds.isEmpty()) return emptyList()
        val condition =
            if (tournamentIds == null) {
                FANTASY_SCORES.SEASON_ID.eq(seasonId.value)
            } else {
                FANTASY_SCORES.SEASON_ID.eq(seasonId.value)
                    .and(FANTASY_SCORES.TOURNAMENT_ID.`in`(tournamentIds.map { it.value }))
            }
        return ctx.dsl.selectFrom(FANTASY_SCORES)
            .where(condition)
            .orderBy(FANTASY_SCORES.TOURNAMENT_ID.asc(), FANTASY_SCORES.POINTS.desc())
            .fetch(::toFantasyScore)
    }

    context(ctx: TransactionContext)
    override fun upsertScore(record: UpsertScore): FantasyScore {
        val upserted =
            ctx.dsl.insertInto(FANTASY_SCORES)
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
                .fetchSingle()
        return toFantasyScore(upserted)
    }

    context(ctx: TransactionContext)
    override fun deleteByTournament(tournamentId: TournamentId): Int =
        ctx.dsl.deleteFrom(FANTASY_SCORES)
            .where(FANTASY_SCORES.TOURNAMENT_ID.eq(tournamentId.value))
            .execute()

    context(ctx: TransactionContext)
    override fun deleteBySeason(seasonId: SeasonId): Int =
        ctx.dsl.deleteFrom(FANTASY_SCORES)
            .where(FANTASY_SCORES.SEASON_ID.eq(seasonId.value))
            .execute()

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
            id = FantasyScoreId(record.required(FANTASY_SCORES.ID)),
            seasonId = SeasonId(record.seasonId),
            teamId = TeamId(record.teamId),
            tournamentId = TournamentId(record.tournamentId),
            golferId = GolferId(record.golferId),
            points = record.required(FANTASY_SCORES.POINTS),
            position = record.required(FANTASY_SCORES.POSITION),
            numTied = record.required(FANTASY_SCORES.NUM_TIED),
            basePayout = record.required(FANTASY_SCORES.BASE_PAYOUT),
            ownershipPct = record.required(FANTASY_SCORES.OWNERSHIP_PCT),
            payout = record.required(FANTASY_SCORES.PAYOUT),
            multiplier = record.required(FANTASY_SCORES.MULTIPLIER),
            calculatedAt = record.required(FANTASY_SCORES.CALCULATED_AT).toInstant(),
        )
}
