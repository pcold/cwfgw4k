package com.cwfgw

import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.golfers.golferRoutes
import com.cwfgw.health.DatabaseHealthProbe
import com.cwfgw.health.HealthProbe
import com.cwfgw.health.healthRoutes
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.leagues.leagueRoutes
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.seasons.seasonRoutes
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.teams.teamRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

fun main() {
    val config = AppConfig.load()
    val database = Database.start(config.db)
    val healthProbe = DatabaseHealthProbe(database.dsl)
    val leagueService = LeagueService(LeagueRepository(database.dsl))
    val golferService = GolferService(GolferRepository(database.dsl))
    val seasonService = SeasonService(SeasonRepository(database.dsl))
    val teamService = TeamService(TeamRepository(database.dsl))
    embeddedServer(Netty, port = config.http.port, host = config.http.host) {
        module(healthProbe, leagueService, golferService, seasonService, teamService)
    }.start(wait = true)
}

@OptIn(ExperimentalSerializationApi::class)
fun Application.module(
    healthProbe: HealthProbe,
    leagueService: LeagueService,
    golferService: GolferService,
    seasonService: SeasonService,
    teamService: TeamService,
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            },
        )
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorBody(cause.message ?: "unknown error"))
        }
    }
    routing {
        route("/api/v1") {
            healthRoutes(healthProbe)
            leagueRoutes(leagueService)
            golferRoutes(golferService)
            seasonRoutes(seasonService)
            teamRoutes(teamService)
        }
    }
}

@Serializable
data class ErrorBody(val error: String)
