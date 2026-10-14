package com.cwfgw.golfers

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.required
import com.cwfgw.jooq.tables.records.GolfersRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import org.jooq.Condition
import org.jooq.impl.DSL

interface GolferRepository {
    context(ctx: TransactionContext)
    fun findAll(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer>

    context(ctx: TransactionContext)
    fun findById(id: GolferId): Golfer?

    context(ctx: TransactionContext)
    fun findByPgaPlayerId(pgaPlayerId: String): Golfer?

    context(ctx: TransactionContext)
    fun create(request: CreateGolferRequest): Golfer
}

fun GolferRepository(): GolferRepository = JooqGolferRepository()

private class JooqGolferRepository : GolferRepository {
    context(ctx: TransactionContext)
    override fun findAll(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer> =
        ctx.dsl.selectFrom(GOLFERS)
            .where(filterConditions(activeOnly, search))
            .orderBy(
                GOLFERS.WORLD_RANKING.asc().nullsLast(),
                GOLFERS.LAST_NAME.asc(),
            )
            .fetch(::toGolfer)

    context(ctx: TransactionContext)
    override fun findById(id: GolferId): Golfer? =
        ctx.dsl.selectFrom(GOLFERS)
            .where(GOLFERS.ID.eq(id.value))
            .fetchOne()
            ?.let(::toGolfer)

    context(ctx: TransactionContext)
    override fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        ctx.dsl.selectFrom(GOLFERS)
            .where(GOLFERS.PGA_PLAYER_ID.eq(pgaPlayerId))
            .fetchOne()
            ?.let(::toGolfer)

    context(ctx: TransactionContext)
    override fun create(request: CreateGolferRequest): Golfer {
        val inserted =
            ctx.dsl.insertInto(GOLFERS)
                .set(GOLFERS.PGA_PLAYER_ID, request.pgaPlayerId)
                .set(GOLFERS.FIRST_NAME, request.firstName)
                .set(GOLFERS.LAST_NAME, request.lastName)
                .set(GOLFERS.COUNTRY, request.country)
                .set(GOLFERS.WORLD_RANKING, request.worldRanking)
                .returning()
                .fetchSingle()
        return toGolfer(inserted)
    }

    private fun filterConditions(
        activeOnly: Boolean,
        search: String?,
    ): Condition {
        val conditions =
            buildList {
                if (activeOnly) add(GOLFERS.ACTIVE.eq(true))
                search?.let { term ->
                    val pattern = "%$term%"
                    add(GOLFERS.FIRST_NAME.likeIgnoreCase(pattern).or(GOLFERS.LAST_NAME.likeIgnoreCase(pattern)))
                }
            }
        return conditions.reduceOrNull(Condition::and) ?: DSL.noCondition()
    }

    private fun toGolfer(record: GolfersRecord): Golfer =
        Golfer(
            id = GolferId(record.required(GOLFERS.ID)),
            pgaPlayerId = record.pgaPlayerId,
            firstName = record.firstName,
            lastName = record.lastName,
            country = record.country,
            worldRanking = record.worldRanking,
            active = record.required(GOLFERS.ACTIVE),
            updatedAt = record.required(GOLFERS.UPDATED_AT).toInstant(),
        )
}
