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
private const val ROOT_FILE_CACHE = "public, max-age=86400"
private val MANIFEST_JSON = ContentType("application", "manifest+json")

/**
 * Vite copies `ui/public/` to the bundle root unhashed, so each name is routed on its own: a root
 * wildcard would swallow the SPA's deep links. StaticRoutesSpec fails when this and that folder differ.
 */
internal val ROOT_FILES: Set<String> =
    setOf(
        "apple-touch-icon.png",
        "favicon-16.png",
        "favicon-32.png",
        "favicon.ico",
        "favicon.svg",
        "icon-192.png",
        "icon-512.png",
        "icon-maskable-192.png",
        "icon-maskable-512.png",
        "manifest.webmanifest",
    )

/**
 * The index is `no-cache` so a stale shell never pins users to an old asset manifest. Hashed Vite output
 * under `/assets/` is content-addressed, so immutable for a year; the [ROOT_FILES] keep their names across
 * releases, so they get a day. Compose [spaFallback] after this so asset misses 404 rather than serve the shell.
 */
fun Route.staticRoutes() {
    get("/") { serveIndex() }
    get("/assets/{file}") { serveBundle("static/assets") }
    get("/static/{file}") { serveBundle("static") }
    ROOT_FILES.forEach { file -> get("/$file") { serveResource("static/$file", ROOT_FILE_CACHE) } }
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
    serveResource("$resourceDir/$file", IMMUTABLE_CACHE)
}

private suspend fun RoutingContext.serveResource(path: String, cacheControl: String) {
    val bytes = readClasspathBytes(path) ?: throw DomainError.NotFound("asset not found")
    call.response.header(HttpHeaders.CacheControl, cacheControl)
    call.respondBytes(bytes, contentTypeFor(path))
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
        file.endsWith(".ico") -> ContentType.Image.XIcon
        file.endsWith(".webmanifest") -> MANIFEST_JSON
        else -> ContentType.Application.OctetStream
    }
