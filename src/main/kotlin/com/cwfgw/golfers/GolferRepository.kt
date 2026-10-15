package com.cwfgw.golfers

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.db.required
import com.cwfgw.jooq.keys.GOLFERS_PGA_PLAYER_ID_KEY
import com.cwfgw.jooq.tables.records.GolfersRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import org.jooq.Condition
import org.jooq.InsertSetMoreStep
import org.jooq.impl.DSL

interface GolferRepository {
    context(ctx: TransactionContext)
    fun findAll(search: String?): List<Golfer>

    context(ctx: TransactionContext)
    fun findById(id: GolferId): Golfer?

    context(ctx: TransactionContext)
    fun findByPgaPlayerId(pgaPlayerId: String): Golfer?

    context(ctx: WriteTransactionContext)
    fun create(request: CreateGolferRequest): Golfer

    /**
     * For a caller inside a larger transaction: a conflicting insert does nothing rather than raise the 23505
     * that would abort the caller's transaction, and the row already holding [CreateGolferRequest.pgaPlayerId]
     * comes back instead.
     */
    context(ctx: WriteTransactionContext)
    fun findOrCreate(request: CreateGolferRequest): Golfer

    /**
     * Null when row [id] already has an id or another row holds [pgaPlayerId]. The UPDATE's WHERE decides, not a
     * read before it, so a concurrent setter of row [id] is waited on and wins; one still inserting [pgaPlayerId]
     * elsewhere surfaces as the 23505 instead.
     */
    context(ctx: WriteTransactionContext)
    fun backfillPgaPlayerId(
        id: GolferId,
        pgaPlayerId: String,
    ): Golfer?
}

fun GolferRepository(): GolferRepository = JooqGolferRepository()

internal val UNIQUE_PGA_PLAYER_ID: String = GOLFERS_PGA_PLAYER_ID_KEY.name

private class JooqGolferRepository : GolferRepository {
    context(ctx: TransactionContext)
    override fun findAll(search: String?): List<Golfer> =
        ctx.dsl.selectFrom(GOLFERS)
            .where(searchCondition(search))
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

    context(ctx: WriteTransactionContext)
    override fun create(request: CreateGolferRequest): Golfer = toGolfer(insert(request).returning().fetchSingle())

    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer {
        val inserted =
            insert(request)
                .onConflictOnConstraint(GOLFERS_PGA_PLAYER_ID_KEY)
                .doNothing()
                .returning()
                .fetchOne()
        if (inserted != null) return toGolfer(inserted)
        val pgaPlayerId = checkNotNull(request.pgaPlayerId) { "an insert without a pga_player_id cannot conflict" }
        val holder = findByPgaPlayerId(pgaPlayerId)
        return checkNotNull(holder) { "the golfer holding a conflicting pga_player_id vanished" }
    }

    context(ctx: WriteTransactionContext)
    override fun backfillPgaPlayerId(
        id: GolferId,
        pgaPlayerId: String,
    ): Golfer? {
        val holder = GOLFERS.`as`("holder")
        return ctx.dsl.update(GOLFERS)
            .set(GOLFERS.PGA_PLAYER_ID, pgaPlayerId)
            .where(GOLFERS.ID.eq(id.value))
            .and(GOLFERS.PGA_PLAYER_ID.isNull)
            .andNotExists(ctx.dsl.selectOne().from(holder).where(holder.PGA_PLAYER_ID.eq(pgaPlayerId)))
            .returning()
            .fetchOne()
            ?.let(::toGolfer)
    }

    context(ctx: WriteTransactionContext)
    private fun insert(request: CreateGolferRequest): InsertSetMoreStep<GolfersRecord> =
        ctx.dsl.insertInto(GOLFERS)
            .set(GOLFERS.PGA_PLAYER_ID, request.pgaPlayerId)
            .set(GOLFERS.FIRST_NAME, request.firstName)
            .set(GOLFERS.LAST_NAME, request.lastName)
            .set(GOLFERS.COUNTRY, request.country)
            .set(GOLFERS.WORLD_RANKING, request.worldRanking)

    private fun searchCondition(search: String?): Condition {
        if (search == null) return DSL.noCondition()
        val pattern = "%$search%"
        return GOLFERS.FIRST_NAME.likeIgnoreCase(pattern).or(GOLFERS.LAST_NAME.likeIgnoreCase(pattern))
    }

    private fun toGolfer(record: GolfersRecord): Golfer =
        Golfer(
            id = GolferId(record.required(GOLFERS.ID)),
            pgaPlayerId = record.pgaPlayerId,
            firstName = record.firstName,
            lastName = record.lastName,
            country = record.country,
            worldRanking = record.worldRanking,
            updatedAt = record.required(GOLFERS.UPDATED_AT).toInstant(),
            tombstonedAt = record.tombstonedAt?.toInstant(),
        )
}
