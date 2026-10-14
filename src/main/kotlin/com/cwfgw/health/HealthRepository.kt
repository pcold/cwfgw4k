package com.cwfgw.health

import com.cwfgw.db.TransactionContext

interface HealthRepository {
    /** Throws the driver's DataAccessException when the database cannot answer. */
    context(ctx: TransactionContext)
    fun ping()
}

fun HealthRepository(): HealthRepository = JooqHealthRepository()

private class JooqHealthRepository : HealthRepository {
    context(ctx: TransactionContext)
    override fun ping() {
        ctx.dsl.selectOne().fetch()
    }
}
