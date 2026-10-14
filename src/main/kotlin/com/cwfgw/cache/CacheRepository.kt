package com.cwfgw.cache

import com.cwfgw.db.TransactionContext
import com.cwfgw.jooq.tables.references.CACHE
import com.cwfgw.jooq.tables.references.CACHE_GENERATION
import org.jooq.JSONB
import org.jooq.impl.DSL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Persistent key/value cache backed by an UNLOGGED Postgres table. The
 * intended use is HTTP GET response caching keyed by the serving revision
 * and the full request URI, and valued by the JSON-serialized response body.
 *
 * TTL semantics: [get] only returns rows whose `expires_at > now()`,
 * so consumers always see fresh-or-nothing. [deleteExpired] is a periodic
 * background sweep — without it the table would grow indefinitely.
 *
 * Postgres JSONB normalizes whitespace on read: a value stored as
 * `{"foo":"bar"}` returns as `{"foo": "bar"}`. The body bytes a client sees
 * on a cache hit are therefore not always byte-identical to what would have
 * been written on a cache miss, but they're always semantically equivalent.
 * This is fine for our consumers; flag it if a future caller relies on
 * exact-byte equivalence (e.g. ETag-based caching).
 *
 * The interface stays free of HTTP concerns. [RequestCache] builds every key
 * and must stay the only caller: a key built anywhere else carries no revision.
 */
interface CacheRepository {
    /** Returns the JSON value if present and not expired, else null. */
    context(ctx: TransactionContext)
    fun get(key: String): String?

    /** Read before a fill fetches, and handed back to [put]. */
    context(ctx: TransactionContext)
    fun generation(): CacheGeneration

    /**
     * UPSERT, only while the generation is still [generation]; false, storing nothing, once a [clear] has advanced
     * it. Locks the generation row `FOR SHARE`, not `FOR KEY SHARE`, which [clear]'s non-key UPDATE wouldn't wait
     * for. Must run in a transaction, or the lock ends before the insert.
     */
    context(ctx: TransactionContext)
    fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean

    /** Delete every row whose [Instant] expiry has already passed. Returns the row count for logging. */
    context(ctx: TransactionContext)
    fun deleteExpired(): Int

    /**
     * Unlike [deleteExpired], takes live rows and every revision's. Advances the generation before deleting: the
     * UPDATE waits out every [put] holding the row, so the DELETE after it sees their rows. Returns the row count.
     */
    context(ctx: TransactionContext)
    fun clear(): Int
}

/** @see CacheRepository.generation */
@JvmInline
value class CacheGeneration(val value: Long)

fun CacheRepository(): CacheRepository = JooqCacheRepository()

private class JooqCacheRepository : CacheRepository {
    context(ctx: TransactionContext)
    override fun get(key: String): String? =
        ctx.dsl.select(CACHE.VALUE)
            .from(CACHE)
            .where(CACHE.KEY.eq(key))
            .and(CACHE.EXPIRES_AT.greaterThan(DSL.currentOffsetDateTime()))
            .fetchOne()
            ?.value1()
            ?.data()

    context(ctx: TransactionContext)
    override fun generation(): CacheGeneration =
        CacheGeneration(ctx.dsl.selectFrom(CACHE_GENERATION).fetchSingle().generation)

    context(ctx: TransactionContext)
    override fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean {
        val current = ctx.dsl.selectFrom(CACHE_GENERATION).forShare().fetchSingle().generation
        if (current != generation.value) return false
        val expiry = OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        ctx.dsl.insertInto(CACHE)
            .set(CACHE.KEY, key)
            .set(CACHE.VALUE, JSONB.valueOf(value))
            .set(CACHE.EXPIRES_AT, expiry)
            .onConflict(CACHE.KEY)
            .doUpdate()
            .set(CACHE.VALUE, JSONB.valueOf(value))
            .set(CACHE.EXPIRES_AT, expiry)
            .execute()
        return true
    }

    context(ctx: TransactionContext)
    override fun deleteExpired(): Int =
        ctx.dsl.deleteFrom(CACHE)
            .where(CACHE.EXPIRES_AT.lessOrEqual(DSL.currentOffsetDateTime()))
            .execute()

    context(ctx: TransactionContext)
    override fun clear(): Int {
        ctx.dsl.update(CACHE_GENERATION)
            .set(CACHE_GENERATION.GENERATION, CACHE_GENERATION.GENERATION.plus(1))
            .execute()
        return ctx.dsl.deleteFrom(CACHE).execute()
    }
}
