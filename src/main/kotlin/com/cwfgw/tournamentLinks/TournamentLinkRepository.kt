package com.cwfgw.tournamentLinks

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.TournamentPlayerOverridesRecord
import com.cwfgw.jooq.tables.references.TOURNAMENT_PLAYER_OVERRIDES
import com.cwfgw.tournaments.TournamentId

interface TournamentLinkRepository {
    context(ctx: TransactionContext)
    fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride>

    /** Empty [ids] returns empty without a query. Rows come in competitor order, not grouped by tournament. */
    context(ctx: TransactionContext)
    fun listByTournaments(ids: Collection<TournamentId>): List<TournamentPlayerOverride>

    context(ctx: WriteTransactionContext)
    fun upsert(
        tournamentId: TournamentId,
        espnCompetitorId: String,
        golferId: GolferId,
    ): TournamentPlayerOverride

    /** @return true if a row was deleted, false if no override existed for that pair. */
    context(ctx: WriteTransactionContext)
    fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Boolean
}

fun TournamentLinkRepository(): TournamentLinkRepository = JooqTournamentLinkRepository()

private class JooqTournamentLinkRepository : TournamentLinkRepository {
    context(ctx: TransactionContext)
    override fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride> =
        listByTournaments(listOf(tournamentId))

    context(ctx: TransactionContext)
    override fun listByTournaments(ids: Collection<TournamentId>): List<TournamentPlayerOverride> {
        if (ids.isEmpty()) return emptyList()
        return ctx.dsl.select(
            TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID,
            TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID,
            TOURNAMENT_PLAYER_OVERRIDES.GOLFER_ID,
        )
            .from(TOURNAMENT_PLAYER_OVERRIDES)
            .where(TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID.`in`(ids.map { it.value }))
            .orderBy(TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID)
            .fetch { row -> toOverride(row.into(TOURNAMENT_PLAYER_OVERRIDES)) }
    }

    context(ctx: WriteTransactionContext)
    override fun upsert(
        tournamentId: TournamentId,
        espnCompetitorId: String,
        golferId: GolferId,
    ): TournamentPlayerOverride {
        val inserted =
            ctx.dsl.insertInto(TOURNAMENT_PLAYER_OVERRIDES)
                .set(TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID, tournamentId.value)
                .set(TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID, espnCompetitorId)
                .set(TOURNAMENT_PLAYER_OVERRIDES.GOLFER_ID, golferId.value)
                .onConflict(
                    TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID,
                    TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID,
                )
                .doUpdate()
                .set(TOURNAMENT_PLAYER_OVERRIDES.GOLFER_ID, golferId.value)
                .returning(
                    TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID,
                    TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID,
                    TOURNAMENT_PLAYER_OVERRIDES.GOLFER_ID,
                )
                .fetchSingle()
        return toOverride(inserted)
    }

    context(ctx: WriteTransactionContext)
    override fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Boolean =
        ctx.dsl.deleteFrom(TOURNAMENT_PLAYER_OVERRIDES)
            .where(TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID.eq(tournamentId.value))
            .and(TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID.eq(espnCompetitorId))
            .execute() > 0

    private fun toOverride(record: TournamentPlayerOverridesRecord): TournamentPlayerOverride =
        TournamentPlayerOverride(
            tournamentId = TournamentId(record.tournamentId),
            espnCompetitorId = record.espnCompetitorId,
            golferId = GolferId(record.golferId),
        )
}
