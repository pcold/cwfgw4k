package com.cwfgw

import com.cwfgw.drafts.DraftService
import com.cwfgw.espn.EspnImportService
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.HealthProbe
import com.cwfgw.leagues.LeagueService
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.TournamentService

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
    val draftService: DraftService,
    val scoringService: ScoringService,
    val espnImportService: EspnImportService,
)
