package com.cwfgw.testing

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * In-memory [Transactor] for service and route specs whose repositories are
 * Fakes that never touch [TransactionContext.dsl]. Both [read] and [update]
 * just run the block with a stub [TransactionContext] — no real transaction
 * is opened, so this won't blow up against a connectionless DSL.
 */
internal class FakeTransactor(
    private val ctx: TransactionContext = TransactionContext(DSL.using(SQLDialect.POSTGRES)),
) : Transactor {
    override suspend fun <A> read(block: suspend context(TransactionContext) () -> A): A =
        with(ctx) { block() }

    override suspend fun <A> update(block: suspend context(TransactionContext) () -> A): A =
        with(ctx) { block() }
}
