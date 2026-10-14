package com.cwfgw.db

import org.jooq.DSLContext

/**
 * Ambient capability handed to repository methods via `context(ctx: TransactionContext)`.
 * Holds the [DSLContext] the call should run against — either the root DSL (for
 * [Transactor.get]'s auto-commit reads) or the per-transaction DSL that
 * [Transactor.read] and [Transactor.update] open.
 */
interface TransactionContext {
    val dsl: DSLContext
}

/**
 * In production only [Transactor.update] provides this; test Fakes mint their own. Every repository write, and
 * any read that takes a row lock, declares it, so calling one from [Transactor.read] or [Transactor.get] is a
 * compile error rather than a `READ ONLY` rejection or an autocommit that isn't atomic with its neighbours.
 */
interface WriteTransactionContext : TransactionContext

fun TransactionContext(dsl: DSLContext): TransactionContext =
    object : TransactionContext {
        override val dsl: DSLContext = dsl
    }

/** @see WriteTransactionContext */
fun WriteTransactionContext(dsl: DSLContext): WriteTransactionContext =
    object : WriteTransactionContext {
        override val dsl: DSLContext = dsl
    }
