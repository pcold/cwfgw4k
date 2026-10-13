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
                .addDecoder(ConfigSecretDecoder())
                .addDecoder(PositiveSecondsDecoder())
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
    val password: ConfigSecret,
    val schema: String,
    val maxPoolSize: Int,
)

data class AuthConfig(
    val sessionSecret: ConfigSecret,
    val sessionMaxAgeSeconds: Long,
    val adminUsername: String?,
    val adminPassword: ConfigSecret?,
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
    val sweepIntervalSeconds: PositiveSeconds,
)

/**
 * One cache per JVM, so each Cloud Run instance holds its own. A `live=true` report bypasses the
 * Postgres response cache, which makes this TTL the whole of how far live data can trail ESPN:
 * 60s by default, shared by the scoreboard and calendar caches.
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
