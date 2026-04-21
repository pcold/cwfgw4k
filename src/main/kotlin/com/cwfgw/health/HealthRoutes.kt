package com.cwfgw.health

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

private val log = KotlinLogging.logger {}

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val database: String,
)

fun Route.healthRoutes(dsl: DSLContext) {
    get("/health") {
        try {
            dsl.selectOne().fetch()
            call.respond(HealthResponse(status = "ok", service = "cwfgw", database = "connected"))
        } catch (e: DataAccessException) {
            log.error(e) { "Health check failed: database unreachable" }
            call.respond(
                HttpStatusCode.InternalServerError,
                HealthResponse(status = "degraded", service = "cwfgw", database = "unreachable"),
            )
        }
    }
}
