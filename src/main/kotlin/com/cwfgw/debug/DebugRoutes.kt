@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.cwfgw.debug

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.management.ManagementFactory

/**
 * Diagnostic surface for in-flight state. Mounted at `/debug` only when
 * [com.cwfgw.config.DebugConfig.enabled] is `true` — in prod the flag
 * stays off and these routes don't register.
 *
 * The on-demand endpoint is useful for inspecting a warm instance, but
 * it's not the diagnostic of last resort: when the Ktor request
 * pipeline itself wedges, this endpoint wedges along with it (Cloud Run
 * 504-at-90s, observed under cold-burst against the live-overlay
 * routes). For those cases the periodic auto-dump launched from
 * `Application.launchDebugProbeLog` is the load-bearing one — it runs
 * on the application coroutine scope and writes to stderr, bypassing
 * the request pipeline entirely.
 */
fun Route.debugRoutes() {
    route("/debug") {
        get("/threads") { getThreads() }
    }
}

private suspend fun RoutingContext.getThreads() {
    val dump = withContext(Dispatchers.IO) { buildDebugDump() }
    call.respondText(dump, ContentType.Text.Plain)
}

/**
 * Build a plain-text dump of every JVM thread plus every active
 * coroutine (when `DebugProbes` is installed). Shared by the on-demand
 * route handler and the background probe-log coroutine.
 */
internal fun buildDebugDump(): String =
    buildString {
        appendLine("== JVM thread dump ==")
        appendLine()
        ManagementFactory.getThreadMXBean()
            .dumpAllThreads(true, true)
            .forEach { info ->
                append(info.toString())
                appendLine()
            }
        appendLine("== Coroutine dump ==")
        appendLine()
        if (DebugProbes.isInstalled) {
            val buffer = ByteArrayOutputStream()
            PrintStream(buffer, true, Charsets.UTF_8).use(DebugProbes::dumpCoroutines)
            append(buffer.toString(Charsets.UTF_8))
        } else {
            appendLine("DebugProbes not installed (DEBUG_ENDPOINTS_ENABLED was false at startup).")
        }
    }
