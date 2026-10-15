package com.cwfgw.testing

import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * A Fake keeps its rows in [fakeTableOf] / [FakeCell] rather than a plain map so it joins this: the first touch
 * inside a [FakeTransactor.update] block saves the collection, and a block that does not return puts it back.
 * A record of calls rather than rows, such as `FakeScoringRepository.scoreUpserts`, stays plain on purpose.
 */
internal class FakeRollback {
    // Identity, not equality: two Fakes holding equal rows are still two collections to put back.
    private val undo = IdentityHashMap<FakeState, () -> Unit>()

    fun <A> transact(block: () -> A): A {
        check(openTransaction.get() == null) { "a FakeTransactor block cannot open a nested transaction" }
        openTransaction.set(this)
        try {
            val result = block()
            // Discarding the undo log is the commit. Nothing catches: which exception ended the block is
            // irrelevant to the undo, and naming one would mean catching Throwable (src/CLAUDE.md).
            undo.clear()
            return result
        } finally {
            openTransaction.remove()
            undo.values.forEach { restore -> restore() }
        }
    }

    private fun enlist(state: FakeState) {
        undo.computeIfAbsent(state) { it.undoToHere() }
    }

    companion object {
        // A Fake reaches its collection through no context parameter, so the open transaction is found
        // dynamically. One thread is scope enough: Transactor's block is not `suspend`.
        private val openTransaction = ThreadLocal<FakeRollback?>()

        /** No-op outside a [FakeTransactor.update] block, where a seeding write stands in for autocommit. */
        fun join(state: FakeState) {
            openTransaction.get()?.enlist(state)
        }
    }
}

/** One Fake collection's participation in [FakeRollback]. */
internal sealed class FakeState {
    protected fun enlist() {
        FakeRollback.join(this)
    }

    abstract fun undoToHere(): () -> Unit
}

/**
 * Every access enlists, reads included: a view such as [MutableMap.values] can write. Concurrent like the maps it
 * replaced, since `RequestCacheSpec` drives one Fake from concurrent callers.
 */
internal fun <K : Any, V : Any> fakeTableOf(): MutableMap<K, V> = FakeTable()

private class FakeTable<K : Any, V : Any> : FakeState(), MutableMap<K, V> {
    private val rows = ConcurrentHashMap<K, V>()

    override val size: Int get() = touched().size
    override val keys: MutableSet<K> get() = touched().keys
    override val values: MutableCollection<V> get() = touched().values
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = touched().entries

    override fun isEmpty(): Boolean = touched().isEmpty()

    override fun containsKey(key: K): Boolean = touched().containsKey(key)

    override fun containsValue(value: V): Boolean = touched().containsValue(value)

    override fun get(key: K): V? = touched()[key]

    override fun put(
        key: K,
        value: V,
    ): V? = touched().put(key, value)

    override fun putAll(from: Map<out K, V>) = touched().putAll(from)

    override fun remove(key: K): V? = touched().remove(key)

    override fun clear() = touched().clear()

    override fun undoToHere(): () -> Unit {
        val saved = HashMap(rows)
        return {
            rows.clear()
            rows.putAll(saved)
        }
    }

    override fun equals(other: Any?): Boolean = rows == other

    override fun hashCode(): Int = rows.hashCode()

    override fun toString(): String = rows.toString()

    private fun touched(): ConcurrentHashMap<K, V> {
        enlist()
        return rows
    }
}

/** A single value a Fake keeps beside its tables, such as a counter. */
internal class FakeCell<T : Any>(initial: T) : FakeState() {
    private val current = AtomicReference(initial)

    fun get(): T = touched().get()

    fun update(transform: (T) -> T): T = touched().updateAndGet(transform)

    override fun undoToHere(): () -> Unit {
        val saved = current.get()
        return { current.set(saved) }
    }

    private fun touched(): AtomicReference<T> {
        enlist()
        return current
    }
}
