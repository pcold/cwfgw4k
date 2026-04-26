package com.cwfgw

import com.cwfgw.admin.AdminService
import com.cwfgw.admin.adminRoutes
import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
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
import com.cwfgw.http.installErrorHandling
import com.cwfgw.http.installRequestLogging
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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

fun main() {
    val config = AppConfig.load()
    requireValidAuthSecret(config.auth)
    val database = Database.start(config.db)
    val httpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = HTTP_CLIENT_CONNECT_TIMEOUT_MS
                requestTimeoutMillis = HTTP_CLIENT_REQUEST_TIMEOUT_MS
            }
        }
    val services = buildServices(config, database, httpClient)
    runBlocking { seedAdminIfEmpty(services.authService, services.userRepository, config.auth) }
    embeddedServer(Netty, port = config.http.port, host = config.http.host) {
        module(services)
    }.start(wait = true)
}

private fun buildServices(
    config: AppConfig,
    database: Database,
    httpClient: HttpClient,
): AppServices {
    val teamService = TeamService(TeamRepository(database.dsl))
    val seasonService = SeasonService(SeasonRepository(database.dsl))
    val tournamentService = TournamentService(TournamentRepository(database.dsl))
    val golferService = GolferService(GolferRepository(database.dsl))
    val userRepository = UserRepository(database.dsl)
    val espnService =
        EspnService(EspnClient(httpClient), tournamentService, golferService, teamService, seasonService)
    val scoringService =
        ScoringService(ScoringRepository(database.dsl), seasonService, tournamentService, teamService)
    val adminService =
        AdminService(seasonService, tournamentService, espnService, golferService, teamService)
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
    val tournamentOpsService = TournamentOpsService(tournamentService, scoringService, espnService)
    val seasonOpsService = SeasonOpsService(seasonService, tournamentService, scoringService)
    return AppServices(
        healthProbe = DatabaseHealthProbe(database.dsl),
        leagueService = LeagueService(LeagueRepository(database.dsl)),
        golferService = golferService,
        seasonService = seasonService,
        teamService = teamService,
        tournamentService = tournamentService,
        tournamentOpsService = tournamentOpsService,
        seasonOpsService = seasonOpsService,
        draftService = DraftService(DraftRepository(database.dsl), teamService),
        scoringService = scoringService,
        espnService = espnService,
        adminService = adminService,
        liveOverlayService = liveOverlayService,
        weeklyReportService = weeklyReportService,
        authService = AuthService(userRepository),
        userRepository = userRepository,
        authSetup =
            AuthSetup(
                sessionSecret = config.auth.sessionSecret.toByteArray(),
                sessionMaxAgeSeconds = config.auth.sessionMaxAgeSeconds,
            ),
    )
}

private const val HTTP_CLIENT_CONNECT_TIMEOUT_MS: Long = 10_000
private const val HTTP_CLIENT_REQUEST_TIMEOUT_MS: Long = 30_000

/**
 * Boot-time guard: refuse to start with a blank session secret. Extracted so
 * tests can exercise the predicate without spinning up the whole app.
 */
internal fun requireValidAuthSecret(auth: com.cwfgw.config.AuthConfig) {
    require(auth.sessionSecret.isNotBlank()) {
        "AUTH_SESSION_SECRET must be set (generate with: openssl rand -hex 32)"
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun Application.module(services: AppServices) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            },
        )
    }
    install(Sessions) {
        cookie<UserSession>("cwfgw_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = services.authSetup.sessionMaxAgeSeconds
            transform(SessionTransportTransformerMessageAuthentication(services.authSetup.sessionSecret))
        }
    }
    install(Authentication) {
        session<UserSession>(SESSION_AUTH_NAME) {
            validate { session ->
                session.userId.toUserId()?.let { id ->
                    services.userRepository.findById(id)?.let(::UserPrincipal)
                }
            }
        }
    }
    installRequestLogging()
    installErrorHandling()
    routing {
        route("/api/v1") {
            healthRoutes(services.healthProbe)
            leagueRoutes(services.leagueService)
            golferRoutes(services.golferService)
            seasonRoutes(services.seasonService)
            teamRoutes(services.teamService)
            tournamentRoutes(services.tournamentService)
            tournamentOpsRoutes(services.tournamentOpsService)
            seasonOpsRoutes(services.seasonOpsService)
            draftRoutes(services.draftService)
            scoringRoutes(services.scoringService)
            espnRoutes(services.espnService)
            adminRoutes(services.adminService)
            reportRoutes(services.weeklyReportService)
            authRoutes(services.authService)
        }
    }
}
