package com.cwfgw.db

import org.jooq.DSLContext

/**
 * Ambient capability handed to repository methods via `context(ctx: TransactionContext)`.
 * Holds the [DSLContext] the call should run against — either the root DSL (for
 * non-transactional reads and writes) or the per-transaction DSL provided by
 * [Transactor.update].
 */
interface TransactionContext {
    val dsl: DSLContext
}

fun TransactionContext(dsl: DSLContext): TransactionContext =
    object : TransactionContext {
        override val dsl: DSLContext = dsl
    }
