package com.cwfgw.db

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
 */
interface Transactor {
    suspend fun <A> get(block: suspend context(TransactionContext) () -> A): A

    suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A

    suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A
}

fun Transactor(dsl: DSLContext): Transactor = JooqTransactor(dsl)

private class JooqTransactor(private val rootDsl: DSLContext) : Transactor {
    private val rootContext: TransactionContext = TransactionContext(rootDsl)

    override suspend fun <A> get(block: suspend context(TransactionContext) () -> A): A =
        with(rootContext) { block() }

    override suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A =
        rootDsl.transactionCoroutine { config ->
            val txDsl = config.dsl()
            // Must run before any other statement in the transaction; PG rejects
            // SET TRANSACTION after the snapshot has been taken by a real query.
            txDsl.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
            with(TransactionContext(txDsl)) { block() }
        }

    override suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A =
        rootDsl.transactionCoroutine { config ->
            with(TransactionContext(config.dsl())) { block() }
        }
}
