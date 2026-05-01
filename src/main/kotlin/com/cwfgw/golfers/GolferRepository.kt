package com.cwfgw.golfers

import com.cwfgw.db.TransactionContext
import com.cwfgw.jooq.tables.records.GolfersRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL

interface GolferRepository {
    context(ctx: TransactionContext)
    suspend fun findAll(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer>

    context(ctx: TransactionContext)
    suspend fun findById(id: GolferId): Golfer?

    context(ctx: TransactionContext)
    suspend fun findByPgaPlayerId(pgaPlayerId: String): Golfer?

    context(ctx: TransactionContext)
    suspend fun create(request: CreateGolferRequest): Golfer

    context(ctx: TransactionContext)
    suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer?
}

fun GolferRepository(): GolferRepository = JooqGolferRepository()

private class JooqGolferRepository : GolferRepository {
    context(ctx: TransactionContext)
    override suspend fun findAll(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer> =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(GOLFERS)
                .where(filterConditions(activeOnly, search))
                .orderBy(
                    GOLFERS.WORLD_RANKING.asc().nullsLast(),
                    GOLFERS.LAST_NAME.asc(),
                )
                .fetch(::toGolfer)
        }

    context(ctx: TransactionContext)
    override suspend fun findById(id: GolferId): Golfer? =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(GOLFERS)
                .where(GOLFERS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toGolfer)
        }

    context(ctx: TransactionContext)
    override suspend fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(GOLFERS)
                .where(GOLFERS.PGA_PLAYER_ID.eq(pgaPlayerId))
                .fetchOne()
                ?.let(::toGolfer)
        }

    context(ctx: TransactionContext)
    override suspend fun create(request: CreateGolferRequest): Golfer =
        withContext(Dispatchers.IO) {
            val inserted =
                ctx.dsl.insertInto(GOLFERS)
                    .set(GOLFERS.PGA_PLAYER_ID, request.pgaPlayerId)
                    .set(GOLFERS.FIRST_NAME, request.firstName)
                    .set(GOLFERS.LAST_NAME, request.lastName)
                    .set(GOLFERS.COUNTRY, request.country)
                    .set(GOLFERS.WORLD_RANKING, request.worldRanking)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for golfers")
            toGolfer(inserted)
        }

    context(ctx: TransactionContext)
    override suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer? =
        withContext(Dispatchers.IO) {
            val changes = updateAssignments(request)
            if (changes.isEmpty()) {
                ctx.dsl.selectFrom(GOLFERS)
                    .where(GOLFERS.ID.eq(id.value))
                    .fetchOne()
                    ?.let(::toGolfer)
            } else {
                ctx.dsl.update(GOLFERS)
                    .set(changes + (GOLFERS.UPDATED_AT to DSL.currentOffsetDateTime()))
                    .where(GOLFERS.ID.eq(id.value))
                    .returning()
                    .fetchOne()
                    ?.let(::toGolfer)
            }
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

    private fun updateAssignments(request: UpdateGolferRequest): Map<Field<*>, Any?> =
        buildMap {
            request.pgaPlayerId?.let { put(GOLFERS.PGA_PLAYER_ID, it) }
            request.firstName?.let { put(GOLFERS.FIRST_NAME, it) }
            request.lastName?.let { put(GOLFERS.LAST_NAME, it) }
            request.country?.let { put(GOLFERS.COUNTRY, it) }
            request.worldRanking?.let { put(GOLFERS.WORLD_RANKING, it) }
            request.active?.let { put(GOLFERS.ACTIVE, it) }
        }

    private fun toGolfer(record: GolfersRecord): Golfer =
        Golfer(
            id = GolferId(checkNotNull(record.id) { "golfers.id is NOT NULL but returned null" }),
            pgaPlayerId = record.pgaPlayerId,
            firstName = record.firstName,
            lastName = record.lastName,
            country = record.country,
            worldRanking = record.worldRanking,
            active = checkNotNull(record.active) { "golfers.active is NOT NULL but returned null" },
            updatedAt =
                checkNotNull(record.updatedAt) {
                    "golfers.updated_at is NOT NULL but returned null"
                }.toInstant(),
        )
}
