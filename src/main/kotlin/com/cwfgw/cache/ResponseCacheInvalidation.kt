package com.cwfgw.cache

import com.cwfgw.users.UserPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.Route
import org.jooq.exception.DataAccessException

private val log = KotlinLogging.logger {}

private val READ_METHODS: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

/**
 * Empties [cache] when a write anywhere under this route succeeds. On the body, not after the send, so the refetch
 * a client makes on reading the answer can't overtake the clear; the write has committed by then, because a `tx`
 * block cannot suspend and responding does.
 */
fun Route.clearResponseCacheOnWrite(cache: RequestCache) {
    install(
        createRouteScopedPlugin(name = "ResponseCacheInvalidation") {
            on(ResponseBodyReadyForSend) { call, content ->
                if (call.isSuccessfulAuthenticatedWrite(content)) clearAfterCommit(cache)
            }
        },
    )
}

// Anonymous calls never clear: `POST /auth/logout` answers 204 to anyone, so any client could empty the cache.
// The status resolves as the engine's commit does; a bare `respond(body)` has set neither yet.
private fun ApplicationCall.isSuccessfulAuthenticatedWrite(content: OutgoingContent): Boolean {
    val status = content.status ?: response.status() ?: HttpStatusCode.OK
    return request.httpMethod !in READ_METHODS && status.isSuccess() && principal<UserPrincipal>() != null
}

// The write has committed, so a failed clear must not turn its answer into a 500 the client would retry.
private suspend fun clearAfterCommit(cache: RequestCache) {
    try {
        cache.clear()
    } catch (e: DataAccessException) {
        log.warn(e) { "cwfgw4k.cache event=uncleared" }
    }
}
