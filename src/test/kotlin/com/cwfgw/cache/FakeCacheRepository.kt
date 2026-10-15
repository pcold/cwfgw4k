package com.cwfgw.cache

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.testing.FakeCell
import com.cwfgw.testing.fakeTableOf
import java.time.Clock
import java.time.Instant

class FakeCacheRepository(
    initial: Map<String, FakeEntry> = emptyMap(),
    private val clock: Clock = Clock.systemUTC(),
) : CacheRepository {
    private val store = fakeTableOf<String, FakeEntry>()
    private val currentGeneration = FakeCell(0L)

    init {
        store.putAll(initial)
    }

    context(ctx: TransactionContext)
    override fun get(key: String): String? {
        val entry = store[key] ?: return null
        return if (entry.expiresAt.isAfter(clock.instant())) entry.value else null
    }

    context(ctx: TransactionContext)
    override fun generation(): CacheGeneration = CacheGeneration(currentGeneration.get())

    context(ctx: WriteTransactionContext)
    override fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean {
        if (currentGeneration.get() != generation.value) return false
        store[key] = FakeEntry(value = value, expiresAt = expiresAt)
        return true
    }

    context(ctx: WriteTransactionContext)
    override fun deleteExpired(): Int {
        val now = clock.instant()
        val expired = store.entries.filter { !it.value.expiresAt.isAfter(now) }
        expired.forEach { store.remove(it.key) }
        return expired.size
    }

    context(ctx: WriteTransactionContext)
    override fun clear(): Int {
        currentGeneration.update { it + 1 }
        val deleted = store.size
        store.clear()
        return deleted
    }

    fun storedKeys(): Set<String> = store.keys.toSet()
}

data class FakeEntry(
    val value: String,
    val expiresAt: Instant,
)
