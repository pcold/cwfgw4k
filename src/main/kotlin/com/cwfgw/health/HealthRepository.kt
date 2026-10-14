package com.cwfgw.health

import com.cwfgw.db.TransactionContext

/**
 * The health probe's one query. It lives here rather than in [DatabaseHealthProbe] because jOOQ stays in
 * repositories.
 */
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
