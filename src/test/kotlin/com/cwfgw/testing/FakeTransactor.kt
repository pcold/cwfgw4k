package com.cwfgw.testing

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * In-memory [Transactor] for service and route specs whose repositories are
 * Fakes that never touch [TransactionContext.dsl]. All three methods just
 * run the block with a stub [TransactionContext] — no real transaction is
 * opened, so this won't blow up against a connectionless DSL.
 */
internal class FakeTransactor(
    private val ctx: TransactionContext = noopTransactionContext,
) : Transactor {
    override suspend fun <A> get(block: suspend context(TransactionContext) () -> A): A =
        with(ctx) { block() }

    override suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A =
        with(ctx) { block() }

    override suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A =
        with(ctx) { block() }
}

/**
 * A [TransactionContext] usable from tests that call a Fake repository directly
 * (rather than going through a service). The wrapped DSL is a connectionless
 * Postgres handle — fine for satisfying the `context(ctx: TransactionContext)`
 * signature on a Fake whose body never reaches into `ctx.dsl`. Never execute
 * SQL through this.
 */
internal val noopTransactionContext: TransactionContext = TransactionContext(DSL.using(SQLDialect.POSTGRES))
