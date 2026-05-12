package com.cwfgw.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addMapSource
import com.sksamuel.hoplite.addResourceSource

data class AppConfig(
    val http: HttpConfig,
    val db: DbConfig,
    val auth: AuthConfig,
    val cache: CacheConfig,
    val espn: EspnConfig,
    val debug: DebugConfig,
) {
    companion object {
        fun load(overrides: Map<String, Any> = emptyMap()): AppConfig =
            ConfigLoaderBuilder.default()
                .apply { if (overrides.isNotEmpty()) addMapSource(overrides) }
                .addResourceSource("/application.yaml")
                .build()
                .loadConfigOrThrow<AppConfig>()
    }
}

data class HttpConfig(val host: String, val port: Int)

data class DbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val schema: String,
    val maxPoolSize: Int,
)

data class AuthConfig(
    val sessionSecret: String,
    val sessionMaxAgeSeconds: Long,
    val adminUsername: String?,
    val adminPassword: String?,
    val cookieSecure: Boolean,
)

/**
 * Tuning for the Postgres-backed request cache. [defaultTtlSeconds] is the
 * default expiry for newly-stored entries; routes can pass an explicit TTL
 * override per call. [sweepIntervalSeconds] controls how often the
 * background coroutine deletes expired rows; setting it to TTL keeps the
 * table at most ~one TTL window of stale rows.
 */
data class CacheConfig(
    val defaultTtlSeconds: Long,
    val sweepIntervalSeconds: Long,
)

/**
 * In-process Caffeine cache fronting the ESPN HTTP client. Sits inside a
 * single JVM (so each Cloud Run instance has its own) and dedupes concurrent
 * scoreboard / calendar fetches to ESPN within the TTL window. Layered
 * below the Postgres response cache: when the response cache misses and the
 * handler reaches into ESPN, this layer serves from in-process if any other
 * request fetched the same date / calendar in the last TTL.
 *
 * Default 300s — short enough for live leaderboards to stay relatively
 * fresh, long enough to absorb fan-out bursts (e.g. N parallel report
 * rebuilds across different cache keys all hitting the same ESPN date).
 */
data class EspnConfig(
    val scoreboardCacheTtlSeconds: Long,
    val scoreboardCacheMaxSize: Long,
)

/**
 * Toggle for diagnostic endpoints that expose runtime state (thread +
 * coroutine dumps). Off by default — production never sets the env var,
 * so the routes don't register. Staging deploys driven by
 * `ops/load-test/cold-burst.sh` flip it on so the harness can capture
 * point-in-time stack snapshots while the service is wedged.
 *
 * `DebugProbes` installation has non-trivial overhead (every coroutine
 * launch records its call site), so its `install()` call is gated on
 * the same flag rather than always-on.
 */
data class DebugConfig(val enabled: Boolean)
