package com.cwfgw

import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.drafts.DraftRepository
import com.cwfgw.drafts.DraftService
import com.cwfgw.drafts.draftRoutes
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
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.scoringRoutes
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.seasons.seasonRoutes
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.teams.teamRoutes
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.tournamentRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

fun main() {
    val config = AppConfig.load()
    val database = Database.start(config.db)
    val teamService = TeamService(TeamRepository(database.dsl))
    val seasonService = SeasonService(SeasonRepository(database.dsl))
    val tournamentService = TournamentService(TournamentRepository(database.dsl))
    val services =
        AppServices(
            healthProbe = DatabaseHealthProbe(database.dsl),
            leagueService = LeagueService(LeagueRepository(database.dsl)),
            golferService = GolferService(GolferRepository(database.dsl)),
            seasonService = seasonService,
            teamService = teamService,
            tournamentService = tournamentService,
            draftService = DraftService(DraftRepository(database.dsl), teamService),
            scoringService =
                ScoringService(
                    repository = ScoringRepository(database.dsl),
                    seasonService = seasonService,
                    tournamentService = tournamentService,
                    teamService = teamService,
                ),
        )
    embeddedServer(Netty, port = config.http.port, host = config.http.host) {
        module(services)
    }.start(wait = true)
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
            draftRoutes(services.draftService)
            scoringRoutes(services.scoringService)
        }
    }
}
