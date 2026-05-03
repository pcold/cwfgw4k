package com.cwfgw.cache

import com.cwfgw.db.TransactionContext
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class FakeCacheRepository(
    initial: Map<String, FakeEntry> = emptyMap(),
    private val clock: Clock = Clock.systemUTC(),
) : CacheRepository {
    private val store = ConcurrentHashMap<String, FakeEntry>()

    init {
        store.putAll(initial)
    }

    context(ctx: TransactionContext)
    override suspend fun get(key: String): String? {
        val entry = store[key] ?: return null
        return if (entry.expiresAt.isAfter(clock.instant())) entry.value else null
    }

    context(ctx: TransactionContext)
    override suspend fun put(
        key: String,
        value: String,
        expiresAt: Instant,
    ) {
        store[key] = FakeEntry(value = value, expiresAt = expiresAt)
    }

    context(ctx: TransactionContext)
    override suspend fun deleteExpired(): Int {
        val now = clock.instant()
        val expired = store.entries.filter { !it.value.expiresAt.isAfter(now) }
        expired.forEach { store.remove(it.key) }
        return expired.size
    }
}

data class FakeEntry(
    val value: String,
    val expiresAt: Instant,
)
