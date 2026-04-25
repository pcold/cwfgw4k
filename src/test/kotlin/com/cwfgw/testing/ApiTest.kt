package com.cwfgw.testing

import com.cwfgw.AppServices
import com.cwfgw.admin.AdminService
import com.cwfgw.drafts.DraftService
import com.cwfgw.drafts.FakeDraftRepository
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.HealthProbe
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.module
import com.cwfgw.reports.WeeklyReportService
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonOpsService
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentOpsService
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.users.AuthService
import com.cwfgw.users.AuthSetup
import com.cwfgw.users.FakeUserRepository
import com.cwfgw.users.LoginRequest
import com.cwfgw.users.NewUser
import com.cwfgw.users.UserRepository
import com.cwfgw.users.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

@OptIn(ExperimentalSerializationApi::class)
private val TEST_JSON =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

/** Shared credentials for [authenticatedApiTest] — seeded by the helper before `block` runs. */
const val TEST_ADMIN_USERNAME: String = "test-admin"
const val TEST_ADMIN_PASSWORD: String = "test-admin-password-not-used-in-prod"

/**
 * Mutable fixture that each test overrides in its `apiTest { ... }` configure block. Defaults every slot
 * to an empty fake-backed service so a spec only has to override the domain it's actually exercising.
 */
class ApiFixture {
    var healthProbe: HealthProbe = HealthProbe { true }
    var leagueService: LeagueService = LeagueService(FakeLeagueRepository())
    var golferService: GolferService = GolferService(FakeGolferRepository())
    var seasonService: SeasonService = SeasonService(FakeSeasonRepository())
    var teamService: TeamService = TeamService(FakeTeamRepository())
    var tournamentService: TournamentService = TournamentService(FakeTournamentRepository())
    var draftService: DraftService = DraftService(FakeDraftRepository(), teamService)
    var scoringService: ScoringService =
        ScoringService(
            repository = FakeScoringRepository(),
            seasonService = seasonService,
            tournamentService = tournamentService,
            teamService = teamService,
        )
    var espnService: EspnService =
        EspnService(
            client = FakeEspnClient(),
            tournamentService = tournamentService,
            golferService = golferService,
            teamService = teamService,
        )
    var adminService: AdminService =
        AdminService(
            seasonService = seasonService,
            tournamentService = tournamentService,
            espnService = espnService,
            golferService = golferService,
            teamService = teamService,
        )
    var weeklyReportService: WeeklyReportService =
        WeeklyReportService(
            seasonService = seasonService,
            tournamentService = tournamentService,
            teamService = teamService,
            golferService = golferService,
            scoringService = scoringService,
        )
    var tournamentOpsService: TournamentOpsService =
        TournamentOpsService(
            tournamentService = tournamentService,
            scoringService = scoringService,
            espnService = espnService,
        )
    var seasonOpsService: SeasonOpsService =
        SeasonOpsService(
            seasonService = seasonService,
            tournamentService = tournamentService,
            scoringService = scoringService,
        )
    var userRepository: UserRepository = FakeUserRepository()
    var authService: AuthService = AuthService(userRepository, cost = TEST_BCRYPT_COST)
    var authSetup: AuthSetup =
        AuthSetup(
            sessionSecret = TEST_SESSION_SECRET.toByteArray(),
            sessionMaxAgeSeconds = TEST_SESSION_MAX_AGE,
        )
}

private const val TEST_BCRYPT_COST = 4
private const val TEST_SESSION_MAX_AGE = 3_600L
private const val TEST_SESSION_SECRET = "test-only-session-secret-not-used-in-prod"

/**
 * Spin up a Ktor test application with every domain wired to fake-backed services; the optional
 * `configure` block overrides specific services (typically the one under test), and `block` runs
 * with an HttpClient configured to use the same SnakeCase JSON serialization as production.
 */
fun apiTest(
    configure: ApiFixture.() -> Unit = {},
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) {
    val fixture = ApiFixture().apply(configure)
    testApplication {
        application { module(fixture.toAppServices()) }
        block(createJsonClient())
    }
}

/**
 * Like [apiTest] but seeds a test admin into the fixture's user repository, logs in via
 * `/api/v1/auth/login`, and hands `block` a cookie-aware client with a live session. Use this
 * for route specs that exercise auth-gated endpoints — the majority of POST/PUT/DELETE paths —
 * so each test doesn't have to do the seed-and-login dance itself.
 *
 * The `configure` block still runs first so specs can override services. If a spec replaces
 * `userRepository` or `authService`, the seed lands in the replaced one — callers should make
 * sure that replacement happens in configure, not after.
 */
fun authenticatedApiTest(
    configure: ApiFixture.() -> Unit = {},
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) {
    val fixture = ApiFixture().apply(configure)
    runBlocking {
        val hash = fixture.authService.hashPassword(TEST_ADMIN_PASSWORD)
        fixture.userRepository.create(
            NewUser(username = TEST_ADMIN_USERNAME, passwordHash = hash, role = UserRole.Admin),
        )
    }
    testApplication {
        application { module(fixture.toAppServices()) }
        val client = createCookieClient()
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = TEST_ADMIN_USERNAME, password = TEST_ADMIN_PASSWORD))
        }
        block(client)
    }
}

private fun ApiFixture.toAppServices(): AppServices =
    AppServices(
        healthProbe = healthProbe,
        leagueService = leagueService,
        golferService = golferService,
        seasonService = seasonService,
        teamService = teamService,
        tournamentService = tournamentService,
        tournamentOpsService = tournamentOpsService,
        seasonOpsService = seasonOpsService,
        draftService = draftService,
        scoringService = scoringService,
        espnService = espnService,
        adminService = adminService,
        weeklyReportService = weeklyReportService,
        authService = authService,
        userRepository = userRepository,
        authSetup = authSetup,
    )

private fun ApplicationTestBuilder.createJsonClient(): HttpClient =
    createClient {
        install(ContentNegotiation) {
            json(TEST_JSON)
        }
    }

private fun ApplicationTestBuilder.createCookieClient(): HttpClient =
    createClient {
        install(ContentNegotiation) {
            json(TEST_JSON)
        }
        install(HttpCookies)
    }
