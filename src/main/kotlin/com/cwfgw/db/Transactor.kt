package com.cwfgw.db

import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

/**
 * Bridges the root [DSLContext] into a [TransactionContext] that repositories
 * can consume via context parameters. Both methods open a real Postgres
 * transaction so the block sees a consistent snapshot across statements:
 *
 * - [read] opens `REPEATABLE READ READ ONLY` — every statement in the block
 *   sees the same snapshot, and the database rejects accidental writes.
 * - [update] opens a default-isolation read/write transaction. The block is
 *   committed atomically on normal return and rolled back if it throws.
 */
interface Transactor {
    suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A

    suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A
}

fun Transactor(dsl: DSLContext): Transactor = JooqTransactor(dsl)

private class JooqTransactor(private val rootDsl: DSLContext) : Transactor {
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
