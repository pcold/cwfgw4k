package com.cwfgw.http

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

private val UUID_SEGMENT =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private val NUMERIC_SEGMENT = Regex("(?<=/)\\d+(?=/|$)")

/**
 * Collapse per-request variation in the path so the `route` label in the
 * Cloud Logging log-based metric stays bounded.
 *
 *  - Vite hashed asset paths under /assets/ collapse to a single bucket — without
 *    this, every deploy's new bundle hash would be a fresh label value.
 *  - UUID segments collapse to :id.
 *  - Pure numeric segments collapse to :n. Lookbehind/lookahead on the slashes
 *    keeps the separator intact so consecutive numeric segments each match on
 *    their own instead of the first one gobbling the slash.
 */
fun normalizeRoute(path: String): String =
    when {
        path.startsWith("/assets/") -> "/assets/*"
        else -> NUMERIC_SEGMENT.replace(UUID_SEGMENT.replace(path, ":id"), ":n")
    }

/**
 * Emit one structured INFO log per request shaped for Cloud Logging log-based
 * metric extraction: `cwfgw4k.request method=X route=Y status=Z duration_ms=N`.
 * The matching metric regex and dashboard config live in `ops/` — changing
 * this format without updating that config will break the api-usage dashboard.
 */
internal fun Application.installRequestLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val method = call.request.httpMethod.value
            val route = normalizeRoute(call.request.path())
            val status = call.response.status()?.value ?: 0
            val durationMs = call.processingTimeMillis()
            "cwfgw4k.request method=$method route=$route status=$status duration_ms=$durationMs"
        }
    }
}
