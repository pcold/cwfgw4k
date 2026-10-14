package com.cwfgw.health

import com.cwfgw.db.Transactor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.exception.DataAccessException

private val log = KotlinLogging.logger {}

/**
 * Queues on the [Transactor] behind every request rather than skipping it: a saturated pool slowing
 * `/health` is the signal, not a reason for a side door (CWF-70).
 */
class DatabaseHealthProbe(
    private val repository: HealthRepository,
    private val tx: Transactor,
) : HealthProbe {
    override suspend fun isDatabaseConnected(): Boolean =
        try {
            tx.get { repository.ping() }
            true
        } catch (e: DataAccessException) {
            log.error(e) { "Database probe failed" }
            false
        }
}
