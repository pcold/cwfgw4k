package com.cwfgw

import com.cwfgw.admin.AdminService
import com.cwfgw.drafts.DraftService
import com.cwfgw.espn.EspnService
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.HealthProbe
import com.cwfgw.leagues.LeagueService
import com.cwfgw.reports.LiveOverlayService
import com.cwfgw.reports.WeeklyReportService
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonOpsService
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.TeamService
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.TournamentOpsService
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.users.AuthService
import com.cwfgw.users.AuthSetup
import com.cwfgw.users.UserRepository

/**
 * Bundle of services and probes that `module()` wires into the Ktor application.
 * Grouping these keeps `module()` below detekt's parameter-count threshold as
 * domains multiply and makes the shape of "what does the app need to boot"
 * legible in one place.
 */
data class AppServices(
    val healthProbe: HealthProbe,
    val leagueService: LeagueService,
    val golferService: GolferService,
    val seasonService: SeasonService,
    val teamService: TeamService,
    val tournamentService: TournamentService,
    val tournamentLinkService: TournamentLinkService,
    val tournamentOpsService: TournamentOpsService,
    val seasonOpsService: SeasonOpsService,
    val draftService: DraftService,
    val scoringService: ScoringService,
    val espnService: EspnService,
    val adminService: AdminService,
    val liveOverlayService: LiveOverlayService,
    val weeklyReportService: WeeklyReportService,
    val authService: AuthService,
    val userRepository: UserRepository,
    val authSetup: AuthSetup,
)
