package com.cwfgw.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

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
     * rolled back when it throws.
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

private val log = KotlinLogging.logger("com.cwfgw.db.Transactor")

private class JooqTransactor(
    private val rootDsl: DSLContext,
    maxPoolSize: Int,
) : Transactor {
    private val rootContext: TransactionContext = TransactionContext(rootDsl)

    // One view shared by `get`, `read` and `update`, so its pool-size cap holds across all three:
    // callers queue here for a slot rather than inside Hikari's `getConnection()`.
    // Not jOOQ's `transactionCoroutine`: its Reactor bridge can deadlock under concurrency (CWF-30).
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
                    with(TransactionContext(config.dsl())) { block() }
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
