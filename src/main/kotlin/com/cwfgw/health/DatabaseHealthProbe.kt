package com.cwfgw.health

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

private val log = KotlinLogging.logger {}

class DatabaseHealthProbe(private val dsl: DSLContext) : HealthProbe {
    override suspend fun isDatabaseConnected(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                dsl.selectOne().fetch()
                true
            } catch (e: DataAccessException) {
                log.error(e) { "Database probe failed" }
                false
            }
        }
}
