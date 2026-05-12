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
 * stays off and these routes don't register. The cold-burst harness
 * (`ops/load-test/cold-burst.sh`) deploys staging with the flag on,
 * curls [getThreads] mid-wedge, and saves the dumps locally for
 * post-mortem.
 *
 * The dump is plain text by design — `gcloud logging read` truncates
 * long structured payloads, and a `curl > /tmp/dump.txt` round trip is
 * the lowest-friction way to capture stack states from a Cloud Run
 * instance we can't shell into.
 */
fun Route.debugRoutes() {
    route("/debug") {
        get("/threads") { getThreads() }
    }
}

private suspend fun RoutingContext.getThreads() {
    val dump =
        withContext(Dispatchers.IO) {
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
        }
    call.respondText(dump, ContentType.Text.Plain)
}
