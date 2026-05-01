package com.cwfgw.tournamentLinks

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.references.TOURNAMENT_PLAYER_OVERRIDES
import com.cwfgw.tournaments.TournamentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Record

interface TournamentLinkRepository {
    context(ctx: TransactionContext)
    suspend fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride>

    context(ctx: TransactionContext)
    suspend fun upsert(
        tournamentId: TournamentId,
        espnCompetitorId: String,
        golferId: GolferId,
    ): TournamentPlayerOverride

    /** @return true if a row was deleted, false if no override existed for that pair. */
    context(ctx: TransactionContext)
    suspend fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Boolean
}

fun TournamentLinkRepository(): TournamentLinkRepository = JooqTournamentLinkRepository()

private class JooqTournamentLinkRepository : TournamentLinkRepository {
    context(ctx: TransactionContext)
    override suspend fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride> =
        withContext(Dispatchers.IO) {
            ctx.dsl.select(
                TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID,
                TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID,
                TOURNAMENT_PLAYER_OVERRIDES.GOLFER_ID,
            )
                .from(TOURNAMENT_PLAYER_OVERRIDES)
                .where(TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID.eq(tournamentId.value))
                .orderBy(TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID)
                .fetch(::toOverride)
        }

    context(ctx: TransactionContext)
    override suspend fun upsert(
        tournamentId: TournamentId,
        espnCompetitorId: String,
        golferId: GolferId,
    ): TournamentPlayerOverride =
        withContext(Dispatchers.IO) {
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
                    .fetchOne() ?: error("INSERT … RETURNING produced no row for tournament_player_overrides")
            toOverride(inserted)
        }

    context(ctx: TransactionContext)
    override suspend fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            ctx.dsl.deleteFrom(TOURNAMENT_PLAYER_OVERRIDES)
                .where(TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID.eq(tournamentId.value))
                .and(TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID.eq(espnCompetitorId))
                .execute() > 0
        }

    private fun toOverride(record: Record): TournamentPlayerOverride =
        TournamentPlayerOverride(
            tournamentId =
                TournamentId(
                    checkNotNull(record[TOURNAMENT_PLAYER_OVERRIDES.TOURNAMENT_ID]) {
                        "tournament_player_overrides.tournament_id is NOT NULL but returned null"
                    },
                ),
            espnCompetitorId =
                checkNotNull(record[TOURNAMENT_PLAYER_OVERRIDES.ESPN_COMPETITOR_ID]) {
                    "tournament_player_overrides.espn_competitor_id is NOT NULL but returned null"
                },
            golferId =
                GolferId(
                    checkNotNull(record[TOURNAMENT_PLAYER_OVERRIDES.GOLFER_ID]) {
                        "tournament_player_overrides.golfer_id is NOT NULL but returned null"
                    },
                ),
        )
}
