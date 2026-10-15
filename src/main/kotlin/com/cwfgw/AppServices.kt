package com.cwfgw

import com.cwfgw.admin.AdminService
import com.cwfgw.admin.AdminSupport
import com.cwfgw.cache.RequestCache
import com.cwfgw.datahealth.DataHealthRepository
import com.cwfgw.datahealth.DataHealthService
import com.cwfgw.datahealth.DataHealthSupport
import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnSupport
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.golfers.GolfersSupport
import com.cwfgw.health.HealthProbe
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.leagues.LeaguesSupport
import com.cwfgw.reports.LiveOverlayService
import com.cwfgw.reports.ReportsSupport
import com.cwfgw.reports.WeeklyReportService
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringSupport
import com.cwfgw.seasons.SeasonOpsService
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.seasons.SeasonsSupport
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.teams.TeamsSupport
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournamentLinks.TournamentLinksSupport
import com.cwfgw.tournaments.TournamentOpsService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentsSupport
import com.cwfgw.users.AuthService
import com.cwfgw.users.AuthSetup
import com.cwfgw.users.LoginThrottle
import com.cwfgw.users.UserRepository
import com.cwfgw.users.UsersSupport

/**
 * Bundle of services and probes that `module()` wires into the Ktor application.
 * Grouping these keeps `module()` below detekt's parameter-count threshold as
 * domains multiply and makes the shape of "what does the app need to boot"
 * legible in one place.
 */
@ConsistentCopyVisibility
data class AppServices private constructor(
    val healthProbe: HealthProbe,
    val leagueService: LeagueService,
    val golferService: GolferService,
    val seasonService: SeasonService,
    val teamService: TeamService,
    val tournamentService: TournamentService,
    val tournamentLinkService: TournamentLinkService,
    val tournamentOpsService: TournamentOpsService,
    val seasonOpsService: SeasonOpsService,
    val espnService: EspnService,
    val adminService: AdminService,
    val dataHealthService: DataHealthService,
    val liveOverlayService: LiveOverlayService,
    val weeklyReportService: WeeklyReportService,
    val authService: AuthService,
    val loginThrottle: LoginThrottle,
    val userRepository: UserRepository,
    val transactor: Transactor,
    val requestCache: RequestCache,
    val sweepIntervalSeconds: Long,
    val authSetup: AuthSetup,
    /**
     * When true, [com.cwfgw.debug.debugRoutes] are mounted at `/debug`
     * and `kotlinx.coroutines.debug.DebugProbes` is installed. Off by
     * default; flipped on by the cold-burst harness via the
     * `DEBUG_ENDPOINTS_ENABLED` env var.
     */
    val debugEnabled: Boolean,
) {
    companion object {
        /**
         * The one service wiring: production, `SeedMain` and the route-spec fixture all call it and vary only the
         * leaves, so a dependency added to a service can't reach one of them and miss another. [reportsTransactor]
         * exists for a spec that counts the report's reads on a transactor nothing else shares.
         */
        @Suppress("LongMethod") // The single wiring list; splitting it scatters what each bundle is given.
        fun from(
            repositories: AppRepositories,
            healthProbe: HealthProbe,
            espnClient: EspnClient,
            transactor: Transactor,
            reportsTransactor: Transactor = transactor,
            bcryptCost: Int,
            loginThrottle: LoginThrottle,
            authSetup: AuthSetup,
            requestCache: RequestCache,
            sweepIntervalSeconds: Long,
            debugEnabled: Boolean,
        ): AppServices =
            with(repositories) {
                val leagues = LeaguesSupport.from(leagueRepository, transactor)
                val golfers = GolfersSupport.from(golferRepository, transactor)
                val teams = TeamsSupport.from(teamRepository, transactor)
                val dataHealth = DataHealthSupport.from(dataHealthRepository, transactor)
                val users = UsersSupport.from(userRepository, transactor, bcryptCost)
                val seasons = SeasonsSupport.from(seasonRepository, tournamentRepository, scoringRepository, transactor)
                val scoring =
                    ScoringSupport.from(scoringRepository, seasonRepository, tournamentRepository, teamRepository)
                val tournamentLinks =
                    TournamentLinksSupport.from(
                        tournamentLinkRepository = tournamentLinkRepository,
                        tournamentRepository = tournamentRepository,
                        golferRepository = golferRepository,
                        transactor = transactor,
                    )
                val espn =
                    EspnSupport.from(
                        espnClient = espnClient,
                        seasonRepository = seasonRepository,
                        golferRepository = golferRepository,
                        teamRepository = teamRepository,
                        tournamentRepository = tournamentRepository,
                        tournamentLinkRepository = tournamentLinkRepository,
                        transactor = transactor,
                    )
                val tournaments =
                    TournamentsSupport.from(
                        tournamentRepository = tournamentRepository,
                        scoringRepository = scoringRepository,
                        scoring = scoring,
                        espn = espn,
                        transactor = transactor,
                    )
                val admin =
                    AdminSupport.from(
                        seasons = seasons,
                        tournaments = tournaments,
                        espn = espn,
                        golfers = golfers,
                        golferRepository = golferRepository,
                        teamRepository = teamRepository,
                        transactor = transactor,
                    )
                val reports =
                    ReportsSupport.from(
                        seasonRepository = seasonRepository,
                        tournamentRepository = tournamentRepository,
                        teamRepository = teamRepository,
                        golferRepository = golferRepository,
                        scoringRepository = scoringRepository,
                        espn = espn,
                        transactor = reportsTransactor,
                    )
                AppServices(
                    healthProbe = healthProbe,
                    leagueService = leagues.leagueService,
                    golferService = golfers.golferService,
                    seasonService = seasons.seasonService,
                    teamService = teams.teamService,
                    tournamentService = tournaments.tournamentService,
                    tournamentLinkService = tournamentLinks.tournamentLinkService,
                    tournamentOpsService = tournaments.tournamentOpsService,
                    seasonOpsService = seasons.seasonOpsService,
                    espnService = espn.espnService,
                    adminService = admin.adminService,
                    dataHealthService = dataHealth.dataHealthService,
                    liveOverlayService = reports.liveOverlayService,
                    weeklyReportService = reports.weeklyReportService,
                    authService = users.authService,
                    loginThrottle = loginThrottle,
                    userRepository = userRepository,
                    transactor = transactor,
                    requestCache = requestCache,
                    sweepIntervalSeconds = sweepIntervalSeconds,
                    authSetup = authSetup,
                    debugEnabled = debugEnabled,
                )
            }
    }
}

/** @see AppServices.from */
data class AppRepositories(
    val leagueRepository: LeagueRepository,
    val golferRepository: GolferRepository,
    val seasonRepository: SeasonRepository,
    val teamRepository: TeamRepository,
    val tournamentRepository: TournamentRepository,
    val scoringRepository: ScoringRepository,
    val tournamentLinkRepository: TournamentLinkRepository,
    val dataHealthRepository: DataHealthRepository,
    val userRepository: UserRepository,
)
