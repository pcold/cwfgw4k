package com.cwfgw.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL

/**
 * `block` is not `suspend` on purpose: it parks a thread on a pooled
 * connection, so suspending work goes around [get] / [read] / [update],
 * never inside one.
 */
interface Transactor {
    /**
     * Auto-commit per statement: it skips [read]'s `BEGIN` / `SET TRANSACTION`
     * / `COMMIT` and has none of its snapshot, so two statements in one block
     * can disagree and two writes are not atomic.
     */
    suspend fun <A> get(block: context(TransactionContext) () -> A): A

    /**
     * `REPEATABLE READ READ ONLY`: every statement sees one snapshot, and the
     * database rejects a write.
     */
    suspend fun <A> read(block: context(TransactionContext) () -> A): A

    /**
     * Read/write at the default isolation: committed when the block returns,
     * rolled back when it throws. A lock wait past [LOCK_TIMEOUT_MS] throws
     * a [DataAccessException] for which [isLockTimeout] holds.
     */
    suspend fun <A> update(block: context(TransactionContext) () -> A): A
}

/**
 * [maxPoolSize] must be the size of the Hikari pool backing [dsl]: it caps the
 * [Dispatchers.IO] view every block runs on, so the blocks in flight never
 * outnumber the pool's connections.
 */
fun Transactor(dsl: DSLContext, maxPoolSize: Int): Transactor = JooqTransactor(dsl, maxPoolSize)

private const val SLOW_TX_THRESHOLD_MS: Long = 3_000

// Above the 1s deadlock_timeout, so a real deadlock still surfaces as 40P01, and above
// SLOW_TX_THRESHOLD_MS, so the holder that timed a waiter out logs slow_tx when it ends.
internal const val LOCK_TIMEOUT_MS: Long = 5_000

/** SQLSTATE `lock_not_available`. Read through jOOQ's cause walk, so a coroutine-recovered copy still matches. */
internal const val LOCK_NOT_AVAILABLE = "55P03"

/**
 * The block's writes did not happen: its transaction rolled back when the waiting statement failed. True
 * while only [Transactor.update] sets a bound and nothing locks `NOWAIT` inside an auto-commit [Transactor.get].
 */
internal fun DataAccessException.isLockTimeout(): Boolean = sqlState() == LOCK_NOT_AVAILABLE

private val log = KotlinLogging.logger("com.cwfgw.db.Transactor")

/**
 * [kind] is the `kind=` token on `cwfgw4k.db.slow_tx` that `ops/` searches for; renaming a variant must not
 * rename it.
 */
private sealed interface TransactionMode {
    val kind: String

    /** No `BEGIN`, so there is no transaction for a prelude to set up. */
    data object AutoCommit : TransactionMode {
        override val kind: String = "get"
    }

    /** [prelude] runs first: PG rejects `SET TRANSACTION` once a query has taken the snapshot. */
    sealed interface Transactional : TransactionMode {
        fun prelude(txDsl: DSLContext)
    }

    /** @see Transactor.read */
    data object Snapshot : Transactional {
        override val kind: String = "read"

        override fun prelude(txDsl: DSLContext) {
            txDsl.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
        }
    }

    /** @see Transactor.update */
    data object BoundedWrite : Transactional {
        override val kind: String = "update"

        override fun prelude(txDsl: DSLContext) {
            txDsl.setLocal(DSL.unquotedName("lock_timeout"), DSL.inline("${LOCK_TIMEOUT_MS}ms")).execute()
        }
    }
}

private class JooqTransactor(
    private val rootDsl: DSLContext,
    maxPoolSize: Int,
) : Transactor {
    private val rootContext: TransactionContext = TransactionContext(rootDsl)

    // Shared by `get`, `read` and `update` so the pool-size cap holds across all three:
    // a block waits here for a slot, never on another block in Hikari's `getConnection()`.
    // Not jOOQ's `transactionCoroutine`: its Reactor bridge can deadlock (CWF-24).
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(maxPoolSize)

    override suspend fun <A> get(block: context(TransactionContext) () -> A): A =
        inTransaction(TransactionMode.AutoCommit, block)

    override suspend fun <A> read(block: context(TransactionContext) () -> A): A =
        inTransaction(TransactionMode.Snapshot, block)

    override suspend fun <A> update(block: context(TransactionContext) () -> A): A =
        inTransaction(TransactionMode.BoundedWrite, block)

    private suspend fun <A> inTransaction(
        mode: TransactionMode,
        block: context(TransactionContext) () -> A,
    ): A =
        timed(mode.kind) {
            withContext(dispatcher) {
                when (mode) {
                    TransactionMode.AutoCommit -> with(rootContext) { block() }
                    is TransactionMode.Transactional ->
                        // The one sanctioned call of the openers detekt.yml forbids, so neither runs
                        // off [dispatcher] or skips its prelude.
                        @Suppress("ForbiddenMethodCall")
                        rootDsl.transactionResult { config ->
                            val txDsl = config.dsl()
                            mode.prelude(txDsl)
                            with(TransactionContext(txDsl)) { block() }
                        }
                }
            }
        }

    /**
     * `ops/alerts/cwfgw4k-5xx-rate.yaml` and `ops/load-test/soak.sh` send a
     * reader to search the logs for `cwfgw4k.db.slow_tx`; rename it there too.
     */
    private suspend inline fun <A> timed(
        kind: String,
        crossinline block: suspend () -> A,
    ): A {
        val started = System.currentTimeMillis()
        try {
            return block()
        } finally {
            val elapsed = System.currentTimeMillis() - started
            if (elapsed >= SLOW_TX_THRESHOLD_MS) {
                log.warn { "cwfgw4k.db.slow_tx kind=$kind duration_ms=$elapsed" }
            }
        }
    }
}
