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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.serializer
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

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
     * Tracks the in-flight loader for each cache key so concurrent misses
     * coalesce onto a single [fetch] call. Bounded by request concurrency
     * (entries are removed in the loader's finally block); not a leak.
     */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /**
     * Cache-aside fetch keyed by [key]. Single-flight per key:
     *  1. `tx.get` checks the Postgres cache. Auto-commit single-statement
     *     read; connection held only for the SELECT itself.
     *  2. On miss, try to claim the in-flight slot for [key] via an atomic
     *     `putIfAbsent` on a [CompletableDeferred]. If we lose the race we
     *     await the existing loader's deferred — no second [fetch], no
     *     second `tx.update`.
     *  3. The winning caller runs [fetch] outside any cache transaction
     *     (critical under load — [fetch] is the real handler that opens
     *     its own service transactions; holding a connection across it
     *     would consume two pool slots per request) and writes via
     *     `tx.update`.
     *  4. The loader completes the deferred (success or failure) and
     *     atomically removes itself from the in-flight map.
     *
     * The hit path does no serialization. [routeTemplate] is the
     * bounded-cardinality label used in the structured log line.
     *
     * Loader exceptions are NOT cached — the deferred fails, all current
     * waiters re-throw, the in-flight entry is removed, and the next call
     * retries fresh. The Postgres entry is only written on success.
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

        val ours = CompletableDeferred<String>()
        val existing = inFlight.putIfAbsent(key, ours)
        if (existing != null) {
            log.info { "cwfgw4k.cache event=coalesced route=$routeTemplate" }
            return existing.await()
        }

        log.info { "cwfgw4k.cache event=miss route=$routeTemplate" }
        // Catching Throwable here is intentional: the deferred MUST be
        // completed (success or failure) so coalesced waiters never hang.
        // We rethrow immediately so the calling coroutine sees the failure
        // exactly as the underlying [fetch] reported it.
        @Suppress("TooGenericExceptionCaught")
        try {
            val fresh = fetch()
            tx.update { repository.put(key, fresh, clock.instant().plus(ttl)) }
            log.info { "cwfgw4k.cache event=put route=$routeTemplate ttl_seconds=${ttl.seconds}" }
            ours.complete(fresh)
            return fresh
        } catch (t: Throwable) {
            ours.completeExceptionally(t)
            throw t
        } finally {
            // Atomic remove only if our deferred is still the registered one,
            // so a same-key request that arrives after we complete but before
            // we clean up doesn't get a stale stub.
            inFlight.remove(key, ours)
        }
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
