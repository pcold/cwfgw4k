package com.cwfgw.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.routing.Route
import org.jooq.exception.DataAccessException
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.TimeSource

private val log = KotlinLogging.logger {}

/**
 * Run by a request, never a timer: Cloud Run throttles CPU between requests, where the connection a sweep opens can
 * stall its TLS handshake and drain the pool. So an idle instance never sweeps, and a read already skips expired rows.
 */
internal class CacheSweep(
    private val cache: RequestCache,
    private val interval: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val nextDue = AtomicReference(timeSource.markNow())

    /**
     * Awaited by the request, not launched beside it, which can answer before the sweep's connection opens. Exactly
     * one concurrent caller sweeps, and a failure is logged rather than failing the request that carried it.
     */
    suspend fun sweepIfDue() {
        val due = nextDue.get()
        if (!due.hasPassedNow() || !nextDue.compareAndSet(due, timeSource.markNow() + interval)) return
        try {
            cache.deleteExpired()
        } catch (e: DataAccessException) {
            log.warn(e) { "Cache sweep failed; the next request due retries it" }
        }
    }
}

/**
 * Before the handler, so the sweep runs on the CPU the request was given. Under the API route, not the application,
 * so a static asset or the SPA shell never waits on the database.
 */
internal fun Route.sweepCacheOnRequests(sweep: CacheSweep) {
    install(createRouteScopedPlugin(name = "CacheSweep") { onCall { sweep.sweepIfDue() } })
}
