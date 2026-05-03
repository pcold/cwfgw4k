package com.cwfgw.cache

import com.cwfgw.db.TransactionContext
import com.cwfgw.jooq.tables.references.CACHE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Persistent key/value cache backed by an UNLOGGED Postgres table. The
 * intended use is HTTP GET response caching keyed by full request URI
 * (`call.request.uri`) and valued by the JSON-serialized response body.
 *
 * Pure-TTL semantics: [get] only returns rows whose `expires_at > now()`,
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
 * The interface stays free of HTTP concerns; the routing layer's wrapper
 * builds keys from the request URI.
 */
interface CacheRepository {
    /** Returns the JSON value if present and not expired, else null. */
    context(ctx: TransactionContext)
    suspend fun get(key: String): String?

    /** UPSERT: insert or replace the entry's value and expiry. */
    context(ctx: TransactionContext)
    suspend fun put(
        key: String,
        value: String,
        expiresAt: Instant,
    )

    /** Delete every row whose [Instant] expiry has already passed. Returns the row count for logging. */
    context(ctx: TransactionContext)
    suspend fun deleteExpired(): Int
}

fun CacheRepository(): CacheRepository = JooqCacheRepository()

private class JooqCacheRepository : CacheRepository {
    context(ctx: TransactionContext)
    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) {
            ctx.dsl.select(CACHE.VALUE)
                .from(CACHE)
                .where(CACHE.KEY.eq(key))
                .and(CACHE.EXPIRES_AT.greaterThan(DSL.currentOffsetDateTime()))
                .fetchOne()
                ?.value1()
                ?.data()
        }

    context(ctx: TransactionContext)
    override suspend fun put(
        key: String,
        value: String,
        expiresAt: Instant,
    ) {
        withContext(Dispatchers.IO) {
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
        }
    }

    context(ctx: TransactionContext)
    override suspend fun deleteExpired(): Int =
        withContext(Dispatchers.IO) {
            ctx.dsl.deleteFrom(CACHE)
                .where(CACHE.EXPIRES_AT.lessOrEqual(DSL.currentOffsetDateTime()))
                .execute()
        }
}
