package com.cwfgw.db

import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * Thin read-only view over [HikariDataSource]'s pool counters, kept
 * separate from the rest of the db code so the periodic metrics logger
 * doesn't take a hard dependency on the concrete `HikariDataSource`
 * type and so test setups using a different `DataSource` see `null`
 * and skip the polling loop entirely.
 *
 * Captured by [Application.launchPoolMetricsLog] in `Main.kt`; not
 * meant to be wired into business code — pool exhaustion belongs to
 * operations, not to product logic.
 */
class PoolMetrics(private val source: HikariDataSource) {
    fun snapshot(): PoolSnapshot {
        val bean = source.hikariPoolMXBean
        return PoolSnapshot(
            active = bean.activeConnections,
            idle = bean.idleConnections,
            waiting = bean.threadsAwaitingConnection,
            total = bean.totalConnections,
        )
    }
}

/**
 * Point-in-time pool counters. `waiting` is the load-bearing one for
 * diagnosing wedge-style incidents — a sustained non-zero value means
 * requests are queueing for connections, which is the canonical symptom
 * we couldn't see during the May 3 incident.
 */
data class PoolSnapshot(
    val active: Int,
    val idle: Int,
    val waiting: Int,
    val total: Int,
)

/**
 * Returns a [PoolMetrics] when the [DataSource] is HikariCP-backed; null
 * otherwise. Tests using non-Hikari datasources get `null` and the
 * Main.kt launcher silently skips the polling loop for them.
 */
fun DataSource.poolMetricsOrNull(): PoolMetrics? = (this as? HikariDataSource)?.let(::PoolMetrics)
