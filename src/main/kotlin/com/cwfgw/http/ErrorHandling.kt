package com.cwfgw.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Wire-level error payload returned by every mapped HTTP error response.
 */
@Serializable
data class ErrorBody(val error: String)

/**
 * Typed errors that surface as specific HTTP responses at the route boundary.
 * Throw one of these from a service or route to get the right status code
 * without each handler carrying its own try/catch.
 */
sealed class DomainError(message: String) : RuntimeException(message) {
    class NotFound(message: String) : DomainError(message)

    class Validation(message: String) : DomainError(message)

    class Conflict(message: String) : DomainError(message)

    /** An upstream service we depend on (e.g. ESPN) returned an error. Maps to 502 Bad Gateway. */
    class BadGateway(message: String) : DomainError(message)
}

private val logger = LoggerFactory.getLogger("com.cwfgw.http.ErrorHandling")

/**
 * Install StatusPages with per-variant DomainError mappings and a last-resort
 * 500 handler that logs the exception and returns a generic message so internal
 * detail doesn't leak to clients.
 *
 * The `exception<Throwable>` handler is the one place in this codebase that
 * catches Throwable on purpose — StatusPages is the canonical HTTP-boundary
 * sink for uncaught exceptions. Do not replicate this pattern in business
 * logic (CLAUDE.md forbids catching Throwable outside framework boundaries).
 */
internal fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<DomainError.NotFound> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorBody(cause.message ?: "not found"))
        }
        exception<DomainError.Validation> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody(cause.message ?: "invalid request"))
        }
        exception<DomainError.Conflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorBody(cause.message ?: "conflict"))
        }
        exception<DomainError.BadGateway> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorBody(cause.message ?: "upstream unavailable"))
        }
        exception<Throwable> { call, cause ->
            logger.error(
                "Unhandled error on {} {}",
                call.request.httpMethod.value,
                call.request.path(),
                cause,
            )
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("Internal server error"))
        }
    }
}
