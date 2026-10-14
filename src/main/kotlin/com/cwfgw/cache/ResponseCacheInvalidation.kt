package com.cwfgw.cache

import com.cwfgw.users.UserPrincipal
import com.cwfgw.users.UserRole
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.Route
import org.jooq.exception.DataAccessException

private val log = KotlinLogging.logger {}

private val READ_METHODS: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

/**
 * Empties [cache] when an admin writes anywhere under this route. Before the answer goes out, not after, so the
 * refetch a client makes on reading it can't overtake the clear; the write has committed by then, because a `tx` block
 * cannot suspend and responding does.
 */
fun Route.clearResponseCacheOnWrite(cache: RequestCache) {
    install(
        createRouteScopedPlugin(name = "ResponseCacheInvalidation") {
            on(ResponseBodyReadyForSend) { call, _ -> if (call.isAdminWrite()) clearAfterCommit(cache) }
            // StatusPages answers a thrown error outside this route's pipeline, where the hook above never runs.
            on(CallFailed) { call, _ -> if (call.isAdminWrite()) clearAfterCommit(cache) }
        },
    )
}

// Whatever the status: an error can follow a commit, as when `confirmSeasonSchedule` fails on its Nth tournament.
// Admins only, or any signed-in user could empty the cache with a write `requireAdmin` refuses.
private fun ApplicationCall.isAdminWrite(): Boolean =
    request.httpMethod !in READ_METHODS && principal<UserPrincipal>()?.user?.role == UserRole.Admin

// The write has committed, so a failed clear must not turn its answer into a 500 the client would retry.
private suspend fun clearAfterCommit(cache: RequestCache) {
    try {
        cache.clear()
    } catch (e: DataAccessException) {
        log.warn(e) { "cwfgw4k.cache event=uncleared" }
    }
}
