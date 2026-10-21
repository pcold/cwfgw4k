package com.cwfgw

import com.cwfgw.admin.adminRoutes
import com.cwfgw.cache.CacheSweep
import com.cwfgw.cache.clearResponseCacheOnWrite
import com.cwfgw.datahealth.dataHealthRoutes
import com.cwfgw.db.PoolMetrics
import com.cwfgw.debug.buildDebugDump
import com.cwfgw.debug.debugRoutes
import com.cwfgw.golfers.golferRoutes
import com.cwfgw.health.healthRoutes
import com.cwfgw.http.appJson
import com.cwfgw.http.installCompression
import com.cwfgw.http.installErrorHandling
import com.cwfgw.http.installRequestLogging
import com.cwfgw.http.spaFallback
import com.cwfgw.http.staticRoutes
import com.cwfgw.leagues.leagueRoutes
import com.cwfgw.reports.reportRoutes
import com.cwfgw.seasons.seasonOpsRoutes
import com.cwfgw.seasons.seasonRoutes
import com.cwfgw.teams.teamRoutes
import com.cwfgw.tournamentLinks.tournamentLinkRoutes
import com.cwfgw.tournaments.tournamentOpsRoutes
import com.cwfgw.tournaments.tournamentRoutes
import com.cwfgw.users.authRoutes
import com.cwfgw.users.installSessionAuth
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Apart from `Main.kt` because no spec can start the process entry point, and every route spec
 * runs this one through `testing/ApiTest.kt` — so nothing here may read the environment.
 */
fun Application.module(
    services: AppServices,
    poolMetrics: PoolMetrics? = null,
) {
    install(ContentNegotiation) {
        json(appJson)
    }
    installSessionAuth(services.authSetup, services.authService)
    installRequestLogging()
    installCompression()
    installErrorHandling()
    sweepCacheOnRequests(CacheSweep(services.requestCache, services.sweepIntervalSeconds.seconds))
    poolMetrics?.let(::launchPoolMetricsLog)
    if (services.debugEnabled) launchDebugProbeLog()
    routing {
        apiRoutes(services)
        if (services.debugEnabled) debugRoutes()
        staticRoutes()
        spaFallback()
    }
}

/** Before the handler, so the sweep runs on the CPU the request was given. @see CacheSweep */
private fun Application.sweepCacheOnRequests(sweep: CacheSweep) {
    intercept(ApplicationCallPipeline.Monitoring) { sweep.sweepIfDue() }
}

/**
 * `ops/` filters logs on the `cwfgw4k.db.pool …` line format, so it is a contract. A sustained
 * non-zero `waiting` means requests are queueing. No catch: a snapshot reads in-memory counters,
 * closed pool included, and throws only on a programming error.
 */
private fun Application.launchPoolMetricsLog(metrics: PoolMetrics) {
    val intervalMs = POOL_METRICS_INTERVAL_SECONDS * 1000L
    launch {
        while (isActive) {
            val snapshot = metrics.snapshot()
            poolMetricsLog.info {
                "cwfgw4k.db.pool active=${snapshot.active} idle=${snapshot.idle} " +
                    "waiting=${snapshot.waiting} total=${snapshot.total}"
            }
            delay(intervalMs)
        }
    }
}

private val poolMetricsLog = KotlinLogging.logger("com.cwfgw.db.PoolMetrics")

private const val POOL_METRICS_INTERVAL_SECONDS: Long = 30

/**
 * Pushed from the application scope, not pulled through `/debug/threads`, so the dump keeps coming
 * when the request pipeline is wedged; `cwfgw4k.debug.probe` opens each for grep. No catch: its
 * documented throws need a security manager, a JVM without lock-usage monitoring, or probes uninstalled mid-dump.
 */
private fun Application.launchDebugProbeLog() {
    val intervalMs = DEBUG_PROBE_INTERVAL_SECONDS * 1000L
    launch {
        while (isActive) {
            debugProbeLog.info { "cwfgw4k.debug.probe\n" + buildDebugDump() }
            delay(intervalMs)
        }
    }
}

private val debugProbeLog = KotlinLogging.logger("com.cwfgw.debug.Probe")

private const val DEBUG_PROBE_INTERVAL_SECONDS: Long = 10

private fun Routing.apiRoutes(services: AppServices) {
    route("/api/v1") {
        clearResponseCacheOnWrite(services.requestCache)
        healthRoutes(services.healthProbe)
        leagueRoutes(services.leagueService)
        golferRoutes(services.golferService)
        seasonRoutes(services.seasonService)
        teamRoutes(services.teamService)
        tournamentRoutes(services.tournamentService)
        tournamentOpsRoutes(services.tournamentOpsService)
        seasonOpsRoutes(services.seasonOpsService)
        adminRoutes(services.adminService)
        dataHealthRoutes(services.dataHealthService)
        tournamentLinkRoutes(services.espnService, services.tournamentLinkService)
        reportRoutes(services.weeklyReportService, services.requestCache)
        authRoutes(services.authService, services.loginThrottle)
    }
}
