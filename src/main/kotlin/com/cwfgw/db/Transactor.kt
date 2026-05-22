package com.cwfgw.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

/**
 * Bridges the root [DSLContext] into a [TransactionContext] that repositories
 * can consume via context parameters. Three entry points, picked by the
 * shape of the work:
 *
 * - [get] runs the block on the auto-commit pool DSL — no `BEGIN` /
 *   `COMMIT` / `SET TRANSACTION` round-trips. The right tool for
 *   single-statement reads (every `findById`, `findAll`, `getRoster` etc.
 *   that doesn't need to coordinate with a sibling read). Three round-trips
 *   cheaper per call than [read]; on a hot path that runs millions of
 *   single-row lookups the difference is large.
 * - [read] opens `REPEATABLE READ READ ONLY` — every statement in the block
 *   sees the same snapshot, and the database rejects accidental writes.
 *   Reach for it when a block makes more than one read and they need to
 *   agree (e.g. team list + per-team rosters built into one report).
 * - [update] opens a default-isolation read/write transaction. The block is
 *   committed atomically on normal return and rolled back if it throws.
 *
 * The `block` is **not** `suspend` — deliberately. Every entry point runs
 * blocking jOOQ work and holds a pooled connection for its duration;
 * suspending while a connection is checked out is the failure mode this
 * contract forbids by construction. Compose suspending work *around*
 * `get`/`read`/`update`, never inside the block.
 *
 * Each entry point is wrapped with a slow-tx watchdog: if a single tx takes
 * longer than [SLOW_TX_THRESHOLD_MS], a structured WARN log line is
 * emitted (`cwfgw4k.db.slow_tx kind=… duration_ms=…`). Cloud Logging picks
 * it up at severity WARN via the stderr appender so the next 90s wedge has
 * a fingerprint we can grep without a debugger.
 */
interface Transactor {
    suspend fun <A> get(block: context(TransactionContext) () -> A): A

    suspend fun <A> read(block: context(TransactionContext) () -> A): A

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
