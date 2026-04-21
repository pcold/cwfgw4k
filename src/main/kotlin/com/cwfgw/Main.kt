package com.cwfgw

import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.health.healthRoutes
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jooq.DSLContext

fun main() {
    val config = AppConfig.load()
    val database = Database.start(config.db)
    embeddedServer(Netty, port = config.http.port, host = config.http.host) {
        module(database.dsl)
    }.start(wait = true)
}

fun Application.module(dsl: DSLContext) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
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
            healthRoutes(dsl)
        }
    }
}

@Serializable
data class ErrorBody(val error: String)
