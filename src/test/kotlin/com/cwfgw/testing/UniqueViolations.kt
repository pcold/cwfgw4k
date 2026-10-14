package com.cwfgw.testing

import org.jooq.exception.DataAccessException
import org.jooq.exception.IntegrityConstraintViolationException
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage

/**
 * What a Fake throws where Postgres would answer 23505, built the way the driver builds one so
 * `updateOrOutraced` reads [constraint] off the same server fields a real violation carries.
 */
internal fun uniqueViolation(constraint: String): DataAccessException {
    val fields = listOf("SERROR", "C23505", "Mduplicate key value violates unique constraint", "n$constraint")
    val driverError = PSQLException(ServerErrorMessage(fields.joinToString(separator = "\u0000", postfix = "\u0000")))
    return IntegrityConstraintViolationException("duplicate key", driverError)
}
