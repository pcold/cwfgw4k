@file:OptIn(ExperimentalCoroutinesApi::class)

package com.cwfgw

import com.cwfgw.admin.AdminService
import com.cwfgw.cache.CacheRepository
import com.cwfgw.cache.RequestCache
import com.cwfgw.config.AppConfig
import com.cwfgw.config.AuthConfig
import com.cwfgw.db.Database
import com.cwfgw.db.Transactor
import com.cwfgw.db.poolMetricsOrNull
import com.cwfgw.drafts.DraftRepository
import com.cwfgw.drafts.DraftService
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnService
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.DatabaseHealthProbe
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.reports.LiveOverlayService
import com.cwfgw.reports.WeeklyReportService
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonOpsService
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.TournamentOpsService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.users.AuthService
import com.cwfgw.users.AuthSetup
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
    val transactor = Transactor(database.dsl, config.db.maxPoolSize)
    val requestCache =
        RequestCache(
            repository = CacheRepository(),
            tx = transactor,
            revision = config.cache.revision,
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
            client = EspnClient(httpClient, config.espn),
            seasonRepository = seasonRepository,
            golferRepository = golferRepository,
            teamRepository = teamRepository,
            tournamentRepository = tournamentRepository,
            linkRepository = linkRepo,
            tx = transactor,
        )
    val scoringRepository = ScoringRepository()
    val scoringService =
        ScoringService(
            scoringRepository,
            seasonRepository,
            tournamentRepository,
            teamRepository,
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
            seasonRepository = seasonRepository,
            tournamentRepository = tournamentRepository,
            teamRepository = teamRepository,
            golferRepository = golferRepository,
            scoringRepository = scoringRepository,
            liveOverlayService = liveOverlayService,
            tx = transactor,
        )
    return AppServices(
        healthProbe = DatabaseHealthProbe(transactor),
        leagueService = LeagueService(LeagueRepository(), transactor),
        golferService = golferService,
        seasonService = seasonService,
        teamService = teamService,
        tournamentService = tournamentService,
        tournamentLinkService = tournamentLinkService,
        tournamentOpsService =
            TournamentOpsService(
                tournamentRepository = tournamentRepository,
                scoringService = scoringService,
                scoringRepository = scoringRepository,
                espnService = espnService,
                tx = transactor,
            ),
        seasonOpsService =
            SeasonOpsService(
                seasonRepository = seasonRepository,
                tournamentRepository = tournamentRepository,
                scoringRepository = scoringRepository,
                tx = transactor,
            ),
        draftService = DraftService(DraftRepository(), teamRepository, transactor),
        espnService = espnService,
        adminService = adminService,
        liveOverlayService = liveOverlayService,
        weeklyReportService = weeklyReportService,
        authService = AuthService(userRepository, transactor),
        userRepository = userRepository,
        transactor = transactor,
        requestCache = requestCache,
        sweepIntervalSeconds = config.cache.sweepIntervalSeconds.value,
        authSetup = config.auth.toAuthSetup(),
        debugEnabled = config.debug.enabled,
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

// The session secret's one unwrap. Its masked toString() compiles here too, and would sign every cookie with the mask.
internal fun AuthConfig.toAuthSetup(): AuthSetup =
    AuthSetup(
        sessionSecret = sessionSecret.cleartext.toByteArray(),
        sessionMaxAgeSeconds = sessionMaxAgeSeconds,
        cookieSecure = cookieSecure,
    )
