package com.cwfgw.cache

import com.cwfgw.db.Transactor
import com.cwfgw.http.appJson
import com.cwfgw.http.normalizeRoute
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.serializer
import java.time.Clock
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * Read-through cache for HTTP GET responses. Routes opt in by wrapping the
 * handler body in [cachedRespond], which builds a key from the request URI
 * (path + query string), looks up the cached JSON body, and on miss
 * delegates to the handler — serializing and storing the result before
 * sending it.
 *
 * Pure-TTL semantics: the underlying [CacheRepository.get] only returns
 * non-expired rows. [deleteExpired] is the periodic sweep that keeps the
 * table small; it's invoked from a background coroutine launched at boot
 * (see Main.kt), not on every read. Mutations (POST / PUT / DELETE) do not
 * invalidate cache entries — the [defaultTtl] window is the maximum
 * staleness consumers can see.
 *
 * Auth-gated GETs that return user-specific data must NOT use this wrapper:
 * the cache key carries no user identity, so two sessions hitting the same
 * URI would share the same cached body.
 *
 * Each hit / miss / put / sweep emits one structured INFO log line shaped
 * for Cloud Logging metric extraction:
 * `cwfgw4k.cache event=… route=… [ttl_seconds=…] [deleted=…]`. The matching
 * metric config lives in `ops/metrics/cwfgw4k_cache.yaml`; changing the
 * format requires updating that config.
 */
class RequestCache(
    private val repository: CacheRepository,
    private val tx: Transactor,
    private val defaultTtl: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Cache-aside fetch keyed by [key]. Three discrete phases:
     *  1. `tx.get` for the hit lookup — auto-commit single-statement read,
     *     no BEGIN / COMMIT / SET TRANSACTION round-trips. Connection is
     *     held only for the SELECT itself.
     *  2. On miss, [fetch] runs OUTSIDE any cache transaction — critical
     *     under load because [fetch] is the actual handler, which itself
     *     opens transactions through services. Holding a connection across
     *     [fetch] would consume two pool slots per request and exhaust the
     *     pool well before request count.
     *  3. `tx.update` to write the entry — short, independent transaction.
     *
     * The hit path does no serialization. [routeTemplate] is the
     * bounded-cardinality label used in the structured log line.
     *
     * Two concurrent requests for the same missing key will both run
     * [fetch] and both write — accepted at this scale for the simpler
     * connection-management story; if the workload changes to make
     * dedupe matter, gate inside `fetch` or front with a Caffeine
     * single-flight layer.
     */
    suspend fun cachedJsonGet(
        key: String,
        routeTemplate: String,
        ttl: Duration = defaultTtl,
        fetch: suspend () -> String,
    ): String {
        val hit = tx.get { repository.get(key) }
        if (hit != null) {
            log.info { "cwfgw4k.cache event=hit route=$routeTemplate" }
            return hit
        }
        log.info { "cwfgw4k.cache event=miss route=$routeTemplate" }
        val fresh = fetch()
        tx.update { repository.put(key, fresh, clock.instant().plus(ttl)) }
        log.info { "cwfgw4k.cache event=put route=$routeTemplate ttl_seconds=${ttl.seconds}" }
        return fresh
    }

    /** Background-sweep entry point. Logs how many rows were deleted for dashboard tracking. */
    suspend fun deleteExpired(): Int {
        val deleted = tx.update { repository.deleteExpired() }
        log.info { "cwfgw4k.cache event=sweep deleted=$deleted" }
        return deleted
    }
}

/**
 * Route-handler helper: caches the JSON response body keyed by the call's
 * full request URI (path + query), using [cache] and the optional [ttl]
 * override. The handler body produces a serializable [T]; the cache stores
 * its JSON bytes. On a hit no serializer runs.
 *
 * Uses [appJson] so the cached bytes match what Ktor's
 * [io.ktor.server.plugins.contentnegotiation.ContentNegotiation] would
 * write for the same value.
 */
suspend inline fun <reified T> RoutingContext.cachedRespond(
    cache: RequestCache,
    ttl: Duration? = null,
    crossinline fetch: suspend () -> T,
) {
    val key = call.request.uri
    val routeTemplate = normalizeRoute(call.request.path())
    val body =
        if (ttl != null) {
            cache.cachedJsonGet(key, routeTemplate, ttl) {
                appJson.encodeToString(serializer<T>(), fetch())
            }
        } else {
            cache.cachedJsonGet(key, routeTemplate) {
                appJson.encodeToString(serializer<T>(), fetch())
            }
        }
    call.respondText(body, ContentType.Application.Json)
}
