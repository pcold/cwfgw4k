package com.cwfgw.testing

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * In-memory [Transactor] for specs whose repositories are Fakes that never touch [TransactionContext.dsl].
 * [update] matches [Transactor.update]: a throw undoes every Fake write the block made ([FakeRollback]), and any
 * return commits, `Result.Err` included. [get] and [read] cannot write, so they open nothing.
 */
internal class FakeTransactor : Transactor {
    override suspend fun <A> get(block: context(TransactionContext) () -> A): A =
        with(noopTransactionContext) { block() }

    override suspend fun <A> read(block: context(TransactionContext) () -> A): A =
        with(noopTransactionContext) { block() }

    override suspend fun <A> update(block: context(WriteTransactionContext) () -> A): A =
        FakeRollback().transact { with(noopTransactionContext) { block() } }
}

/**
 * A [WriteTransactionContext] usable from tests that call a Fake repository directly (rather than going through a
 * service). Its writes stand outside any transaction, like the autocommit statements they replace. The wrapped DSL
 * is a connectionless Postgres handle: never execute SQL through this.
 */
internal val noopTransactionContext: WriteTransactionContext = WriteTransactionContext(DSL.using(SQLDialect.POSTGRES))
