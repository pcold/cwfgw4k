package com.cwfgw.http

import com.cwfgw.db.isLockTimeout
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.util.converters.DataConversionException
import kotlinx.serialization.Serializable
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import java.time.Duration

/** Every mapped HTTP error response but a [RefusalBody] carries this. */
@Serializable
data class ErrorBody(val error: String)

/**
 * [refusal] is the code a client branches on; [error] keeps every body's key so a client reading only
 * that still shows a sentence. A plain string: the throwers depend on this package, not the reverse.
 */
@Serializable
data class RefusalBody(val refusal: String, val error: String)

/**
 * Thrown at the route boundary only, where [installErrorHandling] maps each
 * variant to its status. A service returns `T?` or a `Result` over its own
 * sealed error and the route bridges it: `src/CLAUDE.md` → Error Handling.
 */
sealed class DomainError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** Maps to 404. */
    class NotFound(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /** Maps to 400: a malformed field. */
    class Validation(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /** Maps to 422: each part is valid, but the pairing is one the resource cannot take. */
    class Unprocessable(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /** Maps to 409: the write collides with another row or another write, such as a duplicate key. */
    class Conflict(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /**
     * Maps to 409 with a [RefusalBody]: a business rule declines the request in the resource's present
     * lifecycle state, such as a write to a finalized tournament. Not [Conflict], which nothing collided to cause.
     */
    class Refused(
        message: String,
        val code: String,
        cause: Throwable? = null,
    ) : DomainError(message, cause)

    /** Maps to 429 with a `Retry-After` header built from [retryAfter]. */
    class TooManyRequests(
        message: String,
        val retryAfter: Duration,
        cause: Throwable? = null,
    ) : DomainError(message, cause)

    /** An upstream service we depend on (e.g. ESPN) returned an error or did not respond. Maps to 502 Bad Gateway. */
    class BadGateway(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /** Authentication required or failed — bad credentials, missing session. Maps to 401 Unauthorized. */
    class Unauthorized(message: String, cause: Throwable? = null) : DomainError(message, cause)

    /**
     * Authenticated but lacking the required role — e.g. a non-admin hitting
     * an admin-gated endpoint. Maps to 403 Forbidden, logged at WARN as a denial.
     */
    class Forbidden(message: String, cause: Throwable? = null) : DomainError(message, cause)
}

private val logger = LoggerFactory.getLogger("com.cwfgw.http.ErrorHandling")

/**
 * Netty's query decoder fails with a bare IllegalArgumentException, which the engine wraps under only this message.
 * Matched by message because a missing serializer is an IllegalArgumentException too, and that one is ours.
 */
private const val NETTY_UNDECODABLE_QUERY = "Unable to decode query parameters"

/**
 * Install StatusPages with per-variant DomainError mappings, a request Ktor cannot parse as 400 / 415, a
 * timed-out lock wait as 409, and a last-resort 500 that logs the exception and answers a generic message, so
 * internal detail doesn't leak to clients.
 *
 * Its `exception<Throwable>` handler is one of two sanctioned Throwable catches. The other is the loader's catch
 * in [com.cwfgw.cache.RequestCache.cachedJsonGet], which rethrows and exists so coalesced waiters never hang.
 */
internal fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<DomainError.NotFound> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorBody(cause.message ?: "not found"))
        }
        exception<DomainError.Validation> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody(cause.message ?: "invalid request"))
        }
        exception<DomainError.Unprocessable> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorBody(cause.message ?: "unprocessable request"))
        }
        exception<DomainError.Conflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorBody(cause.message ?: "conflict"))
        }
        exception<DomainError.Refused> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                RefusalBody(refusal = cause.code, error = cause.message ?: "conflict"),
            )
        }
        exception<DomainError.BadGateway> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorBody(cause.message ?: "upstream unavailable"))
        }
        exception<DomainError.Unauthorized> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorBody(cause.message ?: "unauthorized"))
        }
        exception<DomainError.Forbidden> { call, cause ->
            val reason = cause.message ?: "forbidden"
            call.logDeniedAuthorization(reason)
            call.respond(HttpStatusCode.Forbidden, ErrorBody(reason))
        }
        exception<DomainError.TooManyRequests> { call, cause ->
            call.response.header(HttpHeaders.RetryAfter, cause.retryAfter.toRetryAfterSeconds().toString())
            call.respond(HttpStatusCode.TooManyRequests, ErrorBody(cause.message ?: "too many requests"))
        }
        mapMalformedRequests()
        mapLockTimeouts()
        exception<Throwable> { call, cause -> call.respondUnhandled(cause) }
    }
}

private suspend fun ApplicationCall.respondUnhandled(cause: Throwable) {
    logger.error("Unhandled error on {} {}", request.httpMethod.value, request.path(), cause)
    respond(HttpStatusCode.InternalServerError, ErrorBody("Internal server error"))
}

/**
 * Rounding down hands back a deadline that has not arrived, so a client
 * obeying it exactly earns a second 429.
 */
private fun Duration.toRetryAfterSeconds(): Long {
    val partialSecond = if (nano > 0 && seconds < Long.MAX_VALUE) 1L else 0L
    return (seconds + partialSecond).coerceAtLeast(1L)
}

/**
 * Of the 4xx, only 403 adds a line beyond the request log: scan traffic produces 401 and 404 in volume.
 * Never log the body or a header; neither is vetted for PII.
 */
private fun ApplicationCall.logDeniedAuthorization(reason: String) {
    logger.warn(
        "cwfgw4k.forbidden method={} route={} reason=\"{}\"",
        request.httpMethod.value,
        normalizeRoute(request.path()),
        reason,
    )
}

/**
 * Each 415 exception is mapped as a leaf: `PayloadTooLargeException` shares their base. An empty JSON body is not
 * a 415 but a rejected body, like a truncated one, so no `status` handler backs these up.
 */
private fun StatusPagesConfig.mapMalformedRequests() {
    exception<BadRequestException> { call, cause -> call.respondToBadRequest(cause) }
    exception<UnsupportedMediaTypeException> { call, _ -> call.respondUnsupportedMediaType() }
    exception<CannotTransformContentToTypeException> { call, _ -> call.respondUnsupportedMediaType() }
}

/**
 * Not negotiated: an unparseable Accept is one of these faults, and negotiating the answer would raise it again.
 * A client fault's cause quotes what the client sent, so it is neither echoed nor logged.
 */
private suspend fun ApplicationCall.respondToBadRequest(cause: BadRequestException) {
    val fault = cause.clientFault()
    if (fault == null) {
        respondUnhandled(cause)
    } else {
        respondText(appJson.encodeToString(ErrorBody(fault)), ContentType.Application.Json, HttpStatusCode.BadRequest)
    }
}

/**
 * Null for a server fault, a missing serializer among them; a type the conversion service lacks fails with the same
 * [DataConversionException] as a bad enum name, so both stay 500. Walks the chain: stack-trace recovery, on in every
 * test, hands StatusPages a copy caused by the original.
 */
private fun BadRequestException.clientFault(): String? {
    val chain = generateSequence<Throwable>(this) { it.cause }.toList()
    val undecodableUrl = chain.any { it is URLDecodeException || it.message == NETTY_UNDECODABLE_QUERY }
    val badParameter = chain.any { it is MissingRequestParameterException || it is ParameterConversionException }
    return when {
        chain.any { it is ContentConvertException } -> "malformed or invalid request body"
        undecodableUrl -> "malformed request URL"
        chain.any { it is BadContentTypeFormatException } -> "malformed Accept or Content-Type header"
        badParameter && chain.none { it is DataConversionException } -> "missing or invalid request parameter"
        else -> null
    }
}

/**
 * StatusPages answers with the most specific handler, so every other database failure is sent back to
 * [respondUnhandled] here. No retry: the waiter's transaction has already rolled back.
 */
private fun StatusPagesConfig.mapLockTimeouts() {
    exception<DataAccessException> { call, cause ->
        if (cause.isLockTimeout()) call.respondLockTimeout(cause) else call.respondUnhandled(cause)
    }
}

private suspend fun ApplicationCall.respondLockTimeout(cause: DataAccessException) {
    logger.warn(
        "cwfgw4k.db.lock_timeout method={} route={}",
        request.httpMethod.value,
        normalizeRoute(request.path()),
        cause,
    )
    respond(HttpStatusCode.Conflict, ErrorBody("another change is being saved here; try again"))
}

private suspend fun ApplicationCall.respondUnsupportedMediaType() {
    respond(HttpStatusCode.UnsupportedMediaType, ErrorBody("unsupported or missing content type"))
}
