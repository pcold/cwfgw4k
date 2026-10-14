package com.cwfgw.db

import com.cwfgw.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.exception.DataAccessException
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage

private const val UNIQUE_VIOLATION: String = "23505"

private val log = KotlinLogging.logger("com.cwfgw.db.OutracedWrites")

/**
 * Converts a 23505 on [constraint] alone: one SQLSTATE covers every unique key on a table, and beside a key a race
 * can reach sits one only a bug can. The block has rolled back by then, so a loser re-reads in a new transaction.
 */
internal suspend fun <T, E> Transactor.updateOrOutraced(
    constraint: String,
    outraced: () -> E,
    block: context(WriteTransactionContext) () -> Result<T, E>,
): Result<T, E> =
    try {
        update(block)
    } catch (thrown: DataAccessException) {
        if (thrown.violatedUniqueConstraint() != constraint) throw thrown
        log.warn(thrown) { "cwfgw4k.db event=write-outraced" }
        Result.Err(outraced())
    }

/** Through jOOQ's cause walk, so a coroutine-recovered copy of the exception still matches. */
private fun DataAccessException.violatedUniqueConstraint(): String? {
    val serverError: ServerErrorMessage? = getCause(PSQLException::class.java)?.serverErrorMessage
    return serverError?.takeIf { it.sqlState == UNIQUE_VIOLATION }?.constraint
}
