package com.cwfgw

import com.cwfgw.admin.adminRoutes
import com.cwfgw.cache.RequestCache
import com.cwfgw.db.PoolMetrics
import com.cwfgw.debug.buildDebugDump
import com.cwfgw.debug.debugRoutes
import com.cwfgw.drafts.draftRoutes
import com.cwfgw.espn.espnRoutes
import com.cwfgw.golfers.golferRoutes
import com.cwfgw.health.healthRoutes
import com.cwfgw.http.appJson
import com.cwfgw.http.installErrorHandling
import com.cwfgw.http.installRequestLogging
import com.cwfgw.http.spaFallback
import com.cwfgw.http.staticRoutes
import com.cwfgw.leagues.leagueRoutes
import com.cwfgw.reports.reportRoutes
import com.cwfgw.scoring.scoringRoutes
import com.cwfgw.seasons.seasonOpsRoutes
import com.cwfgw.seasons.seasonRoutes
import com.cwfgw.teams.teamRoutes
import com.cwfgw.tournamentLinks.tournamentLinkRoutes
import com.cwfgw.tournaments.tournamentOpsRoutes
import com.cwfgw.tournaments.tournamentRoutes
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.UserPrincipal
import com.cwfgw.users.UserSession
import com.cwfgw.users.authRoutes
import com.cwfgw.users.toUserId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jooq.exception.DataAccessException
import java.sql.SQLException

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
    install(Sessions) {
        cookie<UserSession>("cwfgw_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            // `Secure` so the cookie only travels over HTTPS — prevents
            // accidental leakage if any path on the domain ever hits HTTP.
            // Configurable via AUTH_COOKIE_SECURE so local dev against
            // http://localhost:8080 still works.
            cookie.secure = services.authSetup.cookieSecure
            // `SameSite=Lax` blocks cross-site form POSTs from carrying the
            // cookie; same-site navigation (top-level GETs) still attaches
            // it so login redirects keep working. Lax over Strict because
            // the post-login bounce is a same-origin GET that needs the
            // session to be present.
            cookie.extensions["SameSite"] = "Lax"
            cookie.maxAgeInSeconds = services.authSetup.sessionMaxAgeSeconds
            transform(SessionTransportTransformerMessageAuthentication(services.authSetup.sessionSecret))
        }
    }
    install(Authentication) {
        session<UserSession>(SESSION_AUTH_NAME) {
            validate { session ->
                session.userId.toUserId()?.let { id ->
                    services.authService.findById(id)?.let(::UserPrincipal)
                }
            }
        }
    }
    installRequestLogging()
    installErrorHandling()
    launchCacheSweep(services.requestCache, services.sweepIntervalSeconds)
    poolMetrics?.let(::launchPoolMetricsLog)
    if (services.debugEnabled) launchDebugProbeLog()
    routing {
        apiRoutes(services)
        if (services.debugEnabled) debugRoutes()
        staticRoutes()
        spaFallback()
    }
}

/**
 * Launch the periodic cache sweep on the application's coroutine scope so it
 * inherits the lifecycle of the embedded server. The loop logs each
 * iteration via [RequestCache.deleteExpired]; failures don't tear the
 * application down — we log and try again next interval.
 */
private fun Application.launchCacheSweep(
    requestCache: RequestCache,
    sweepIntervalSeconds: Long,
) {
    val intervalMs = sweepIntervalSeconds * 1000L
    launch {
        while (isActive) {
            try {
                requestCache.deleteExpired()
            } catch (e: SQLException) {
                cacheSweepLog.warn(e) { "Cache sweep failed; retrying after interval" }
            } catch (e: DataAccessException) {
                cacheSweepLog.warn(e) { "Cache sweep failed; retrying after interval" }
            }
            delay(intervalMs)
        }
    }
}

private val cacheSweepLog = KotlinLogging.logger("com.cwfgw.cache.Sweep")

/**
 * Periodically log a structured snapshot of the HikariCP pool counters so
 * Cloud Logging captures connection pressure during incidents. The
 * `waiting` field is the load-bearing one — a sustained non-zero value
 * means requests are queueing for a connection, which is the canonical
 * symptom of pool exhaustion the May 3 incident lacked visibility into.
 *
 * Format: `cwfgw4k.db.pool active=N idle=M waiting=W total=T`. Metrics
 * derive from this via REGEXP_EXTRACT in the same shape as
 * `cwfgw4k.cache` and `cwfgw4k.request` log lines.
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
 * Periodically write a JVM thread + coroutine dump to stderr when
 * debug endpoints are enabled. Runs on the application coroutine
 * scope, so it keeps emitting even when the Ktor request pipeline is
 * wedged — that's the whole point of pushing instead of pulling. The
 * on-demand `/debug/threads` route is for warm-instance inspection;
 * this loop is for wedge forensics. See CWF-21.
 *
 * Each line starts with `cwfgw4k.debug.probe` so the dump is
 * greppable. The body is multi-line plain text (thread states + the
 * `DebugProbes` coroutine report).
 */
private fun Application.launchDebugProbeLog() {
    val intervalMs = DEBUG_PROBE_INTERVAL_SECONDS * 1000L
    launch {
        while (isActive) {
            @Suppress("TooGenericExceptionCaught")
            try {
                debugProbeLog.info { "cwfgw4k.debug.probe\n" + buildDebugDump() }
            } catch (t: Throwable) {
                debugProbeLog.warn(t) { "Debug probe dump failed; retrying after interval" }
            }
            delay(intervalMs)
        }
    }
}

private val debugProbeLog = KotlinLogging.logger("com.cwfgw.debug.Probe")

private const val DEBUG_PROBE_INTERVAL_SECONDS: Long = 10

private fun Routing.apiRoutes(services: AppServices) {
    route("/api/v1") {
        healthRoutes(services.healthProbe)
        leagueRoutes(services.leagueService)
        golferRoutes(services.golferService)
        seasonRoutes(services.seasonService)
        teamRoutes(services.teamService)
        tournamentRoutes(services.tournamentService, services.requestCache)
        tournamentOpsRoutes(services.tournamentOpsService)
        seasonOpsRoutes(services.seasonOpsService)
        draftRoutes(services.draftService)
        scoringRoutes(services.scoringService, services.requestCache)
        espnRoutes(services.espnService)
        adminRoutes(services.adminService)
        tournamentLinkRoutes(services.espnService, services.tournamentLinkService)
        reportRoutes(services.weeklyReportService, services.requestCache)
        authRoutes(services.authService)
    }
}
