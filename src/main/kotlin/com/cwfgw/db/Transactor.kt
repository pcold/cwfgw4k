package com.cwfgw.db

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

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
 * Each entry point is wrapped with a slow-tx watchdog: if a single tx takes
 * longer than [SLOW_TX_THRESHOLD_MS], a structured WARN log line is
 * emitted (`cwfgw4k.db.slow_tx kind=… duration_ms=…`). Cloud Logging picks
 * it up at severity WARN via the stderr appender so the next 90s wedge has
 * a fingerprint we can grep without a debugger.
 */
interface Transactor {
    suspend fun <A> get(block: suspend context(TransactionContext) () -> A): A

    suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A

    suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A
}

fun Transactor(dsl: DSLContext): Transactor = JooqTransactor(dsl)

private const val SLOW_TX_THRESHOLD_MS: Long = 3_000

private val log = KotlinLogging.logger("com.cwfgw.db.Transactor")

private class JooqTransactor(private val rootDsl: DSLContext) : Transactor {
    private val rootContext: TransactionContext = TransactionContext(rootDsl)

    override suspend fun <A> get(block: suspend context(TransactionContext) () -> A): A =
        timed("get") { with(rootContext) { block() } }

    override suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A =
        timed("read") {
            rootDsl.transactionCoroutine { config ->
                val txDsl = config.dsl()
                // Must run before any other statement in the transaction; PG rejects
                // SET TRANSACTION after the snapshot has been taken by a real query.
                txDsl.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
                with(TransactionContext(txDsl)) { block() }
            }
        }

    override suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A =
        timed("update") {
            rootDsl.transactionCoroutine { config ->
                with(TransactionContext(config.dsl())) { block() }
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
