package com.cwfgw

import com.cwfgw.admin.AdminService
import com.cwfgw.admin.adminRoutes
import com.cwfgw.cache.CacheRepository
import com.cwfgw.cache.RequestCache
import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.db.PoolMetrics
import com.cwfgw.db.Transactor
import com.cwfgw.db.poolMetricsOrNull
import com.cwfgw.drafts.DraftRepository
import com.cwfgw.drafts.DraftService
import com.cwfgw.drafts.draftRoutes
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.espnRoutes
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.golfers.golferRoutes
import com.cwfgw.health.DatabaseHealthProbe
import com.cwfgw.health.healthRoutes
import com.cwfgw.http.appJson
import com.cwfgw.http.installErrorHandling
import com.cwfgw.http.installRequestLogging
import com.cwfgw.http.spaFallback
import com.cwfgw.http.staticRoutes
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.leagues.leagueRoutes
import com.cwfgw.reports.LiveOverlayService
import com.cwfgw.reports.WeeklyReportService
import com.cwfgw.reports.reportRoutes
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.scoringRoutes
import com.cwfgw.seasons.SeasonOpsService
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.seasons.seasonOpsRoutes
import com.cwfgw.seasons.seasonRoutes
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.teams.teamRoutes
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournamentLinks.tournamentLinkRoutes
import com.cwfgw.tournaments.TournamentOpsService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.tournamentOpsRoutes
import com.cwfgw.tournaments.tournamentRoutes
import com.cwfgw.users.AuthService
import com.cwfgw.users.AuthSetup
import com.cwfgw.users.SESSION_AUTH_NAME
import com.cwfgw.users.UserPrincipal
import com.cwfgw.users.UserRepository
import com.cwfgw.users.UserSession
import com.cwfgw.users.authRoutes
import com.cwfgw.users.seedAdminIfEmpty
import com.cwfgw.users.toUserId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
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
import kotlinx.coroutines.runBlocking
import org.jooq.exception.DataAccessException
import java.security.KeyStore
import java.sql.SQLException
import java.time.Duration
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun main() {
    val config = AppConfig.load()
    requireValidAuthSecret(config.auth)
    val database = Database.start(config.db)
    val httpClient = buildHttpClient()
    val services = buildServices(config, database, httpClient)
    runBlocking { seedAdminIfEmpty(services.authService, services.userRepository, services.transactor, config.auth) }
    embeddedServer(Netty, port = config.http.port, host = config.http.host) {
        module(services, database.dataSource.poolMetricsOrNull())
    }.start(wait = true)
}

@Suppress("LongMethod")
internal fun buildServices(
    config: AppConfig,
    database: Database,
    httpClient: HttpClient,
): AppServices {
    val transactor = Transactor(database.dsl)
    val requestCache =
        RequestCache(
            repository = CacheRepository(),
            tx = transactor,
            defaultTtl = Duration.ofSeconds(config.cache.defaultTtlSeconds),
        )
    val teamRepository = TeamRepository()
    val teamService = TeamService(teamRepository, transactor)
    val seasonRepository = SeasonRepository()
    val seasonService = SeasonService(seasonRepository, transactor)
    val tournamentRepository = TournamentRepository()
    val tournamentService = TournamentService(tournamentRepository, transactor)
    val golferRepository = GolferRepository()
    val golferService = GolferService(golferRepository, transactor)
    val userRepository = UserRepository()
    val linkRepo = TournamentLinkRepository()
    val tournamentLinkService =
        TournamentLinkService(linkRepo, tournamentRepository, golferRepository, transactor)
    val espnService =
        EspnService(
            EspnClient(httpClient, config.espn),
            tournamentService,
            golferService,
            teamService,
            seasonService,
            tournamentLinkService,
        )
    val scoringService =
        ScoringService(
            ScoringRepository(),
            seasonRepository,
            tournamentRepository,
            teamRepository,
            transactor,
        )
    val adminService =
        AdminService(
            tx = transactor,
            seasonService = seasonService,
            tournamentService = tournamentService,
            espnService = espnService,
            golferService = golferService,
            golferRepository = golferRepository,
            teamRepository = teamRepository,
        )
    val liveOverlayService = LiveOverlayService(espnService)
    val weeklyReportService =
        WeeklyReportService(
            seasonService,
            tournamentService,
            teamService,
            golferService,
            scoringService,
            liveOverlayService,
        )
    return AppServices(
        healthProbe = DatabaseHealthProbe(database.dsl),
        leagueService = LeagueService(LeagueRepository(), transactor),
        golferService = golferService,
        seasonService = seasonService,
        teamService = teamService,
        tournamentService = tournamentService,
        tournamentLinkService = tournamentLinkService,
        tournamentOpsService = TournamentOpsService(tournamentService, scoringService, espnService),
        seasonOpsService = SeasonOpsService(seasonService, tournamentService, scoringService),
        draftService = DraftService(DraftRepository(), teamRepository, transactor),
        scoringService = scoringService,
        espnService = espnService,
        adminService = adminService,
        liveOverlayService = liveOverlayService,
        weeklyReportService = weeklyReportService,
        authService = AuthService(userRepository, transactor),
        userRepository = userRepository,
        transactor = transactor,
        requestCache = requestCache,
        sweepIntervalSeconds = config.cache.sweepIntervalSeconds,
        authSetup =
            AuthSetup(
                sessionSecret = config.auth.sessionSecret.toByteArray(),
                sessionMaxAgeSeconds = config.auth.sessionMaxAgeSeconds,
                cookieSecure = config.auth.cookieSecure,
            ),
    )
}

private const val HTTP_CLIENT_CONNECT_TIMEOUT_MS: Long = 10_000
private const val HTTP_CLIENT_REQUEST_TIMEOUT_MS: Long = 30_000

/**
 * Build the outbound HTTP client used for ESPN calls (and any future external
 * service). On macOS we initialize the trust manager from the system Keychain
 * so corporate/MITM proxies (Zscaler, etc.) that have a trusted root in the
 * user's keychain don't fail TLS validation. On other platforms (Linux Cloud
 * Run, etc.) the keychain lookup throws and we fall through to the JDK default
 * trust store, which is what we want there.
 */
internal fun buildHttpClient(): HttpClient {
    val trustManager = systemKeychainTrustManager() ?: defaultJdkTrustManager()
    return HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = HTTP_CLIENT_CONNECT_TIMEOUT_MS
            requestTimeoutMillis = HTTP_CLIENT_REQUEST_TIMEOUT_MS
        }
        engine {
            https {
                this.trustManager = trustManager
            }
        }
    }
}

private fun systemKeychainTrustManager(): X509TrustManager? =
    try {
        val keychain = KeyStore.getInstance("KeychainStore", "Apple")
        keychain.load(null, null)
        firstX509(TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keychain) })
    } catch (_: Exception) {
        null
    }

private fun defaultJdkTrustManager(): X509TrustManager? {
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(null as KeyStore?)
    return firstX509(tmf)
}

private fun firstX509(tmf: TrustManagerFactory): X509TrustManager? =
    tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()

/**
 * Boot-time guard: refuse to start with a blank session secret. Extracted so
 * tests can exercise the predicate without spinning up the whole app.
 */
internal fun requireValidAuthSecret(auth: com.cwfgw.config.AuthConfig) {
    require(auth.sessionSecret.isNotBlank()) {
        "AUTH_SESSION_SECRET must be set (generate with: openssl rand -hex 32)"
    }
}

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
    routing {
        apiRoutes(services)
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
