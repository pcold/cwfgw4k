package com.cwfgw.db

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.IMetricsTracker
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import com.zaxxer.hikari.metrics.PoolStats
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sql.DataSource

private val acquireLog = KotlinLogging.logger("com.cwfgw.db.SlowAcquire")

private const val SLOW_ACQUIRE_THRESHOLD_MS: Long = 500

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

/**
 * Hikari [MetricsTrackerFactory] that emits one structured WARN line for
 * every connection acquisition slower than [SLOW_ACQUIRE_THRESHOLD_MS]:
 * `cwfgw4k.db.slow_acquire duration_ms=N`. Routine fast acquisitions
 * (the typical case) stay silent so we don't drown the request log.
 *
 * Wired in [Database.start]. The signal we care about is whether
 * pool-side waiting (vs. query-side latency vs. tx-coordination latency)
 * is what's eating a request — without this they're indistinguishable.
 */
object SlowAcquireMetricsTrackerFactory : MetricsTrackerFactory {
    override fun create(
        poolName: String,
        poolStats: PoolStats,
    ): IMetricsTracker = SlowAcquireTracker
}

private object SlowAcquireTracker : IMetricsTracker {
    override fun recordConnectionAcquiredNanos(elapsedAcquiredNanos: Long) {
        val elapsedMs = elapsedAcquiredNanos / NANOS_PER_MS
        if (elapsedMs >= SLOW_ACQUIRE_THRESHOLD_MS) {
            acquireLog.warn { "cwfgw4k.db.slow_acquire duration_ms=$elapsedMs" }
        }
    }
}

private const val NANOS_PER_MS: Long = 1_000_000
