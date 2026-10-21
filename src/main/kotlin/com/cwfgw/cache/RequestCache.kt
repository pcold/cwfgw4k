package com.cwfgw.cache

import com.cwfgw.db.Transactor
import com.cwfgw.db.isLockTimeout
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
import org.jooq.exception.DataAccessException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * Read-through cache for HTTP GET responses. Routes opt in by wrapping the
 * handler body in [cachedRespond], which hands over the request URI (path +
 * query string) as the key, looks up the cached JSON body, and on miss
 * delegates to the handler — serializing and storing the result before
 * sending it.
 *
 * The underlying [CacheRepository.get] only returns non-expired rows.
 * [deleteExpired] is the sweep that keeps the table small; [CacheSweep] runs
 * it from a request at most once per interval, not on every read.
 *
 * A write invalidates: [clearResponseCacheOnWrite] runs [clear] before any
 * admin write under `/api/v1` answers. The generation it advances lives in
 * Postgres, so a fill that began before it stores nothing on any instance.
 *
 * A deploy does invalidate: [revision] prefixes every stored key, so a body
 * one revision encoded is never served by another. Not a table clear at
 * boot — the instances of an overlapping rollout would empty each other.
 *
 * Auth-gated GETs that return user-specific data must NOT use this wrapper:
 * the cache key carries no user identity, so two sessions hitting the same
 * URI would share the same cached body.
 *
 * Each cache event emits one structured log line shaped for Cloud Logging
 * metric extraction:
 * `cwfgw4k.cache event=… route=… [ttl_seconds=…] [deleted=…]`. The matching
 * metric config lives in `ops/metrics/cwfgw4k_cache.yaml`; changing the
 * format requires updating that config.
 */
class RequestCache(
    private val repository: CacheRepository,
    private val tx: Transactor,
    private val revision: String,
    private val defaultTtl: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Tracks the in-flight loader for each cache key and generation so
     * concurrent misses coalesce onto a single [fetch] call, and a read after
     * a [clear] never joins a fill from before it. Bounded by request
     * concurrency (entries are removed in the loader's finally block); not a
     * leak.
     */
    private val inFlight = ConcurrentHashMap<InFlightKey, CompletableDeferred<String>>()

    /**
     * Cache-aside fetch keyed by [revision] and [key] — joined here, the only
     * function that hands [repository] a key, so no caller can skip the
     * revision. Single-flight per key:
     *  1. `tx.get` checks the Postgres cache. Auto-commit single-statement
     *     read; connection held only for the SELECT itself.
     *  2. On miss, read the cache generation, then try to claim the in-flight
     *     slot for [key] in it via an atomic `putIfAbsent` on a [CompletableDeferred].
     *     If we lose the race we await the existing loader's deferred — no
     *     second [fetch], no second `tx.update`.
     *  3. The winning caller runs [fetch] outside any cache transaction
     *     (critical under load — [fetch] is the real handler that opens
     *     its own service transactions; holding a connection across it
     *     would consume two pool slots per request) and writes via
     *     `tx.update`, which stores nothing if a [clear] ran meanwhile.
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
        val storedKey = versioned(key)
        val hit = tx.get { repository.get(storedKey) }
        if (hit != null) {
            log.info { "cwfgw4k.cache event=hit route=$routeTemplate" }
            return hit
        }

        val loadGeneration = tx.get { repository.generation() }
        val flightKey = InFlightKey(loadGeneration, storedKey)
        val ours = CompletableDeferred<String>()
        val existing = inFlight.putIfAbsent(flightKey, ours)
        if (existing != null) {
            log.info { "cwfgw4k.cache event=coalesced route=$routeTemplate" }
            return existing.await()
        }

        log.info { "cwfgw4k.cache event=miss route=$routeTemplate" }
        // Catching Throwable here is intentional: the deferred MUST be
        // completed (success or failure) so coalesced waiters never hang.
        // We rethrow immediately so the calling coroutine sees the failure
        // exactly as the underlying [fetch] reported it.
        //
        // CWF-22: the fetch enter/exit/error trio bookends the user-supplied
        // lambda. A wedge that lands in code outside DebugProbes' visibility
        // (jOOQ Reactor bridge, Caffeine CompletableFuture) won't show up in
        // background dumps; the only way to confirm "we entered fetch and
        // never came back" is from log lines on each side of the call.
        @Suppress("TooGenericExceptionCaught")
        try {
            val fetchStarted = System.currentTimeMillis()
            log.info { "cwfgw4k.cache event=fetch-enter route=$routeTemplate" }
            val fresh = fetch()
            log.info {
                "cwfgw4k.cache event=fetch-exit route=$routeTemplate " +
                    "duration_ms=${System.currentTimeMillis() - fetchStarted}"
            }
            store(storedKey, fresh, loadGeneration, routeTemplate, ttl)
            ours.complete(fresh)
            return fresh
        } catch (t: Throwable) {
            // Log every exit-via-throw so a wedge that bypasses the
            // installErrorHandling sink (cancellation, dispatcher-internal
            // failure) still leaves a trail. The throwable is attached at
            // WARN so Cloud Logging severity filters surface it.
            log.warn(t) { "cwfgw4k.cache event=error route=$routeTemplate" }
            ours.completeExceptionally(t)
            throw t
        } finally {
            // Atomic remove only if our deferred is still the registered one,
            // so a same-key request that arrives after we complete but before
            // we clean up doesn't get a stale stub.
            inFlight.remove(flightKey, ours)
        }
    }

    /**
     * A put that waits out the lock timeout leaves the entry unstored rather than failing the GET that
     * already holds its answer. `unstored`, not a hyphenated event: the metric extracts `[a-z]+`.
     */
    private suspend fun store(
        storedKey: String,
        fresh: String,
        loadGeneration: CacheGeneration,
        routeTemplate: String,
        ttl: Duration,
    ) {
        val stored =
            try {
                tx.update { repository.put(storedKey, fresh, clock.instant().plus(ttl), loadGeneration) }
            } catch (e: DataAccessException) {
                if (!e.isLockTimeout()) throw e
                log.warn(e) { "cwfgw4k.cache event=unstored route=$routeTemplate" }
                return
            }
        if (stored) {
            log.info { "cwfgw4k.cache event=put route=$routeTemplate ttl_seconds=${ttl.seconds}" }
        } else {
            log.info { "cwfgw4k.cache event=discarded route=$routeTemplate" }
        }
    }

    // A space, because a request URI cannot hold one: the last space in a stored
    // key always ends the revision, whatever characters the revision has.
    private fun versioned(key: String): String = "$revision $key"

    /** Logs how many rows were deleted for dashboard tracking. @see CacheSweep */
    suspend fun deleteExpired(): Int {
        val deleted = tx.update { repository.deleteExpired() }
        log.info { "cwfgw4k.cache event=sweep deleted=$deleted" }
        return deleted
    }

    /** Every revision's rows go, not just this one's. */
    suspend fun clear(): Int {
        val deleted = tx.update { repository.clear() }
        log.info { "cwfgw4k.cache event=clear deleted=$deleted" }
        return deleted
    }
}

private data class InFlightKey(
    val generation: CacheGeneration,
    val storedKey: String,
)

/**
 * Route-handler helper: caches the JSON response body keyed by [cache]'s
 * revision and the call's full request URI (path + query), with the optional
 * [ttl] override. The handler body produces a serializable [T]; the cache stores
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
