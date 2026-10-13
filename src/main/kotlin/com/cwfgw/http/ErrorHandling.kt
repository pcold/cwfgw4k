package com.cwfgw.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.contentType
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
 * Thrown at the route boundary only, where [installErrorHandling] maps each
 * variant to its status. A service returns `T?` or a `Result` over its own
 * sealed error and the route bridges it: `src/CLAUDE.md` → Error Handling.
 */
sealed class DomainError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class NotFound(message: String, cause: Throwable? = null) : DomainError(message, cause)

    class Validation(message: String, cause: Throwable? = null) : DomainError(message, cause)

    class Conflict(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /** An upstream service we depend on (e.g. ESPN) returned an error. Maps to 502 Bad Gateway. */
    class BadGateway(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /** Authentication required or failed — bad credentials, missing session. Maps to 401 Unauthorized. */
    class Unauthorized(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /**
     * Authenticated but lacking the required role — e.g. a non-admin hitting
     * an admin-gated endpoint. Maps to 403 Forbidden.
     */
    class Forbidden(message: String, cause: Throwable? = null) : DomainError(message, cause)
}

private val logger = LoggerFactory.getLogger("com.cwfgw.http.ErrorHandling")

/**
 * Install StatusPages with per-variant DomainError mappings, Ktor's receive
 * failures as 400 / 415, and a last-resort 500 handler that logs the exception
 * and returns a generic message so internal detail doesn't leak to clients.
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
        exception<DomainError.Unauthorized> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorBody(cause.message ?: "unauthorized"))
        }
        exception<DomainError.Forbidden> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorBody(cause.message ?: "forbidden"))
        }
        mapReceiveFailures()
        exception<Throwable> { call, cause -> call.respondUnhandled(cause) }
    }
}

private suspend fun ApplicationCall.respondUnhandled(cause: Throwable) {
    logger.error("Unhandled error on {} {}", request.httpMethod.value, request.path(), cause)
    respond(HttpStatusCode.InternalServerError, ErrorBody("Internal server error"))
}

/**
 * [UnsupportedMediaTypeException] is mapped as a leaf: `PayloadTooLargeException` shares its base. A typed
 * receive's mismatch never arrives as an exception — Ktor 3.2's engine answers it a bare 415, which the status
 * handler fills.
 */
private fun StatusPagesConfig.mapReceiveFailures() {
    exception<BadRequestException> { call, cause -> call.respondToBadRequest(cause) }
    exception<UnsupportedMediaTypeException> { call, _ -> call.respondUnsupportedMediaType() }
    status(HttpStatusCode.UnsupportedMediaType) { call, _ -> call.respondToBareUnsupportedMediaType() }
}

/**
 * Ktor wraps whatever a converter throws in [BadRequestException], a missing serializer included, and raises it
 * for an undecodable URL too: only a [ContentConvertException] cause is the body's fault. That cause quotes the
 * body, so it is neither echoed nor logged.
 */
private suspend fun ApplicationCall.respondToBadRequest(cause: BadRequestException) {
    if (cause.isRejectedBody()) respondMalformedBody() else respondUnhandled(cause)
}

/**
 * Walks the chain because coroutine stack-trace recovery, on wherever assertions are and so in every test,
 * hands StatusPages a copy whose cause is the original exception.
 */
private fun BadRequestException.isRejectedBody(): Boolean =
    generateSequence<Throwable>(this) { it.cause }.any { it is ContentConvertException }

/**
 * The JSON converter returns null for an empty body, so Ktor reports one as a content-type mismatch: a bare 415
 * on a request labeled JSON is that body, and not its label, at fault.
 */
private suspend fun ApplicationCall.respondToBareUnsupportedMediaType() {
    val labeledJson = request.contentType().match(ContentType.Application.Json)
    if (labeledJson) respondMalformedBody() else respondUnsupportedMediaType()
}

private suspend fun ApplicationCall.respondMalformedBody() {
    respond(HttpStatusCode.BadRequest, ErrorBody("malformed or invalid request body"))
}

private suspend fun ApplicationCall.respondUnsupportedMediaType() {
    respond(HttpStatusCode.UnsupportedMediaType, ErrorBody("unsupported or missing content type"))
}
