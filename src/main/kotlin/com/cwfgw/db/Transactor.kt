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
     * Auto-commit: none of [read]'s `BEGIN` / `SET TRANSACTION` / `COMMIT`
     * round-trips, and none of its snapshot — two statements in one block
     * can disagree.
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
 * Build a [Transactor] over [dsl]. [maxPoolSize] must be the size of the
 * Hikari pool backing [dsl]: blocking JDBC is confined to a [Dispatchers.IO]
 * view capped at exactly that many concurrent coroutines, so a coroutine
 * that wins a dispatcher slot is always able to check out a connection.
 */
fun Transactor(dsl: DSLContext, maxPoolSize: Int): Transactor = JooqTransactor(dsl, maxPoolSize)

private const val SLOW_TX_THRESHOLD_MS: Long = 3_000

private val log = KotlinLogging.logger("com.cwfgw.db.Transactor")

private class JooqTransactor(
    private val rootDsl: DSLContext,
    maxPoolSize: Int,
) : Transactor {
    private val rootContext: TransactionContext = TransactionContext(rootDsl)

    // CWF-24 / CWF-30: blocking jOOQ is confined to exactly one place — a
    // `Dispatchers.IO` view capped at the connection-pool size. `read` and
    // `update` run the blocking `transactionResult`: a transaction parks one
    // thread on the JDBC socket for its duration and frees it on return.
    // There is no Reactor-to-coroutine bridge to wedge, so the worst case
    // under concurrency degrades to a predictable throughput throttle —
    // coroutines queue on the dispatcher — never a deadlock. Sizing the cap
    // to the pool keeps the dispatcher the single backpressure point: a
    // coroutine holding a slot can always acquire a connection, so no thread
    // parks inside Hikari's `getConnection()`.
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
     * `cwfgw4k.db.slow_tx` is the string `ops/alerts/cwfgw4k-5xx-rate.yaml`
     * sends on-call to search the logs for; rename it there too.
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
