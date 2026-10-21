package com.cwfgw.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addMapSource
import com.sksamuel.hoplite.addResourceSource

data class AppConfig(
    val http: HttpConfig,
    val db: DbConfig,
    val auth: AuthConfig,
    val loginThrottle: LoginThrottleConfig,
    val cache: CacheConfig,
    val espn: EspnConfig,
    val debug: DebugConfig,
) {
    companion object {
        /** @throws InvalidConfigException listing every rule the decoded config breaks, not only the first. */
        fun load(overrides: Map<String, Any> = emptyMap()): AppConfig =
            decode(overrides).also { config ->
                requireNoViolations(config.authViolations() + config.sizingViolations())
            }

        /**
         * Skips the auth rules: the seed never serves a request, so it neither signs a session nor seeds an admin,
         * and a dev without either configured can still seed.
         */
        fun loadForSeed(overrides: Map<String, Any> = emptyMap()): AppConfig =
            decode(overrides).also { config -> requireNoViolations(config.sizingViolations()) }

        // Not default(): since Hoplite 3.0 that ranks every environment variable above these sources, so an
        // exported DB_SCHEMA would beat an override. The environment reaches config only through ${...} substitution.
        private fun decode(overrides: Map<String, Any>): AppConfig =
            ConfigLoaderBuilder.defaultWithoutPropertySources()
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
) {
    /**
     * Log this, never [jdbcUrl]: pgjdbc accepts `password=` and `sslpassword=` as query parameters, and
     * the query string is dropped whole so a credential parameter added later can't slip through.
     */
    val jdbcUrlWithoutQuery: String get() = jdbcUrl.substringBefore('?')
}

data class AuthConfig(
    val sessionSecret: ConfigSecret,
    val sessionMaxAgeSeconds: Long,
    val adminUsername: String?,
    val adminPassword: ConfigSecret?,
    val cookieSecure: Boolean,
) {
    /** Blank counts as unset: the YAML defaults both to an empty string, so an unexported pair decodes blank. */
    val configuredAdminUsername: String? get() = adminUsername?.takeIf { it.isNotBlank() }

    /** @see configuredAdminUsername */
    val configuredAdminPassword: ConfigSecret? get() = adminPassword?.takeIf { it.isNotBlank() }
}

/** One limit and one window for both keys a failure counts against: the submitted username and the client address. */
data class LoginThrottleConfig(
    val maxFailures: Int,
    val windowSeconds: Long,
)

/**
 * [defaultTtlSeconds] is the expiry a route's own TTL overrides. [sweepIntervalSeconds] is the least time between two
 * sweeps of expired rows, each run by a request: at the TTL, a busy table holds about one TTL window of them.
 * [deployRevision] is blank off Cloud Run, the only host that supplies one per deploy.
 */
data class CacheConfig(
    val defaultTtlSeconds: Long,
    val sweepIntervalSeconds: PositiveSeconds,
    val deployRevision: String,
) {
    /** Off Cloud Run, [buildId] stands in, so a rebuild never reads a body an older build encoded. @see readBuildId */
    fun revision(buildId: String?): String = deployRevision.ifBlank { buildId ?: LOCAL_REVISION }
}

internal const val LOCAL_REVISION: String = "local"

/**
 * One cache per JVM, so each Cloud Run instance holds its own. A `live=true` report during a
 * tournament's live window bypasses the Postgres response cache, so this TTL is how far it can trail ESPN:
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
