@file:OptIn(ExperimentalCoroutinesApi::class)

package com.cwfgw

import com.cwfgw.cache.CacheRepository
import com.cwfgw.cache.RequestCache
import com.cwfgw.config.AppConfig
import com.cwfgw.config.AuthConfig
import com.cwfgw.config.LoginThrottleConfig
import com.cwfgw.datahealth.DataHealthRepository
import com.cwfgw.db.Database
import com.cwfgw.db.Transactor
import com.cwfgw.db.poolMetricsOrNull
import com.cwfgw.espn.EspnClient
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.health.DatabaseHealthProbe
import com.cwfgw.health.HealthRepository
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.users.AuthService
import com.cwfgw.users.AuthSetup
import com.cwfgw.users.LoginThrottle
import com.cwfgw.users.UserRepository
import com.cwfgw.users.seedAdminIfEmpty
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.time.Duration
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun main() {
    val config = AppConfig.load()
    // Install before any coroutines are launched so DebugProbes sees their creation sites.
    // Gated on the same flag that mounts /debug/threads — the probes have non-trivial
    // overhead and prod doesn't need them.
    if (config.debug.enabled) DebugProbes.install()
    val database = Database.start(config.db)
    val httpClient = buildHttpClient()
    val services = buildServices(config, database, httpClient)
    @Suppress("ForbiddenMethodCall") // the server entry point, where src/CLAUDE.md allows runBlocking
    runBlocking { seedAdminIfEmpty(services.authService, services.userRepository, services.transactor, config.auth) }
    embeddedServer(Netty, port = config.http.port, host = config.http.host) {
        module(services, database.dataSource.poolMetricsOrNull())
    }.start(wait = true)
}

internal fun buildServices(
    config: AppConfig,
    database: Database,
    httpClient: HttpClient,
): AppServices {
    val transactor = Transactor(database.dsl, config.db.maxPoolSize)
    return AppServices.from(
        repositories = postgresRepositories(),
        healthProbe = DatabaseHealthProbe(HealthRepository(), transactor),
        espnClient = EspnClient(httpClient, config.espn),
        transactor = transactor,
        bcryptCost = AuthService.DEFAULT_COST,
        loginThrottle = config.loginThrottle.toLoginThrottle(),
        authSetup = config.auth.toAuthSetup(),
        requestCache =
            RequestCache(
                repository = CacheRepository(),
                tx = transactor,
                revision = config.cache.revision,
                defaultTtl = Duration.ofSeconds(config.cache.defaultTtlSeconds),
            ),
        sweepIntervalSeconds = config.cache.sweepIntervalSeconds.value,
        debugEnabled = config.debug.enabled,
    )
}

private fun postgresRepositories(): AppRepositories =
    AppRepositories(
        leagueRepository = LeagueRepository(),
        golferRepository = GolferRepository(),
        seasonRepository = SeasonRepository(),
        teamRepository = TeamRepository(),
        tournamentRepository = TournamentRepository(),
        scoringRepository = ScoringRepository(),
        tournamentLinkRepository = TournamentLinkRepository(),
        dataHealthRepository = DataHealthRepository(),
        userRepository = UserRepository(),
    )

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

private fun LoginThrottleConfig.toLoginThrottle(): LoginThrottle =
    LoginThrottle(maxFailures = maxFailures, window = Duration.ofSeconds(windowSeconds))

// The session secret's one unwrap. Its masked toString() compiles here too, and would sign every cookie with the mask.
internal fun AuthConfig.toAuthSetup(): AuthSetup =
    AuthSetup(
        sessionSecret = sessionSecret.cleartext.toByteArray(),
        sessionMaxAgeSeconds = sessionMaxAgeSeconds,
        cookieSecure = cookieSecure,
    )
