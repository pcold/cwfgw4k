package com.cwfgw.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

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

private const val BOUNDED_LOCK_WAITS = "SET LOCAL lock_timeout = '${LOCK_TIMEOUT_MS}ms'"

/** SQLSTATE `lock_not_available`. Read through jOOQ's cause walk, so a coroutine-recovered copy still matches. */
internal const val LOCK_NOT_AVAILABLE = "55P03"

/** The block's writes did not happen: its transaction rolled back when the waiting statement failed. */
internal fun DataAccessException.isLockTimeout(): Boolean = sqlState() == LOCK_NOT_AVAILABLE

private val log = KotlinLogging.logger("com.cwfgw.db.Transactor")

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
        timed("get") {
            withContext(dispatcher) {
                with(rootContext) { block() }
            }
        }

    override suspend fun <A> read(block: context(TransactionContext) () -> A): A =
        timed("read") {
            withContext(dispatcher) {
                rootDsl.transactionResult { config ->
                    val txDsl = config.dsl()
                    // Must run before any other statement in the transaction; PG rejects
                    // SET TRANSACTION after the snapshot has been taken by a real query.
                    txDsl.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
                    with(TransactionContext(txDsl)) { block() }
                }
            }
        }

    override suspend fun <A> update(block: context(TransactionContext) () -> A): A =
        timed("update") {
            withContext(dispatcher) {
                rootDsl.transactionResult { config ->
                    val txDsl = config.dsl()
                    txDsl.execute(BOUNDED_LOCK_WAITS)
                    with(TransactionContext(txDsl)) { block() }
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
