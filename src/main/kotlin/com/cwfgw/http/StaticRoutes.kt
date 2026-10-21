package com.cwfgw.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val IMMUTABLE_CACHE = "public, max-age=31536000, immutable"
private const val INDEX_CACHE = "no-cache"
private const val INDEX_RESOURCE = "static/index.html"

/**
 * Serve the React UI bundled at `static/` on the classpath. Index returns
 * `Cache-Control: no-cache` so a stale shell never pins users to an old
 * bundle/asset manifest. Hashed Vite output under `/assets/` is
 * content-addressed, so it's safe to mark immutable for a year.
 *
 * Mounted at the application root alongside `/api/v1`. Compose [spaFallback]
 * after this so explicit asset misses still 404 instead of falling through
 * to the SPA shell.
 */
fun Route.staticRoutes() {
    get("/") { serveIndex() }
    get("/assets/{file}") { serveBundle("static/assets") }
    get("/static/{file}") { serveBundle("static") }
}

/**
 * Catch-all for unmatched GETs: anything not under `/api/` returns the React
 * shell so deep links like `/admin` or `/scoreboard` survive a browser refresh.
 * Anything under `/api/` returns 404 so genuine API misses surface instead of
 * being masked by the SPA fallback.
 */
fun Route.spaFallback() {
    get("{path...}") { serveShellOrApiMiss() }
}

private suspend fun RoutingContext.serveShellOrApiMiss() {
    val segments = call.parameters.getAll("path").orEmpty()
    if (segments.firstOrNull() == "api") throw DomainError.NotFound("route not found")
    serveIndex()
}

private suspend fun RoutingContext.serveIndex() {
    val bytes = readClasspathBytes(INDEX_RESOURCE) ?: throw DomainError.NotFound("index not found")
    call.response.header(HttpHeaders.CacheControl, INDEX_CACHE)
    call.respondBytes(bytes, ContentType.Text.Html)
}

private suspend fun RoutingContext.serveBundle(resourceDir: String) {
    val file = call.parameters["file"] ?: throw DomainError.NotFound("asset not found")
    val bytes = readClasspathBytes("$resourceDir/$file") ?: throw DomainError.NotFound("asset not found")
    call.response.header(HttpHeaders.CacheControl, IMMUTABLE_CACHE)
    call.respondBytes(bytes, contentTypeFor(file))
}

/**
 * This file's own loader, not the thread's context loader, which on [Dispatchers.IO] belongs to
 * whichever pool thread runs the read rather than to the request.
 */
private suspend fun readClasspathBytes(path: String): ByteArray? =
    withContext(Dispatchers.IO) {
        StaticRoutesMarker::class.java.classLoader
            .getResourceAsStream(path)
            ?.use { stream -> stream.readAllBytes() }
    }

private object StaticRoutesMarker

private fun contentTypeFor(file: String): ContentType =
    when {
        file.endsWith(".js") -> ContentType.Application.JavaScript
        file.endsWith(".css") -> ContentType.Text.CSS
        file.endsWith(".html") -> ContentType.Text.Html
        file.endsWith(".svg") -> ContentType.Image.SVG
        file.endsWith(".png") -> ContentType.Image.PNG
        else -> ContentType.Application.OctetStream
    }
