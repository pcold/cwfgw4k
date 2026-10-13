package com.cwfgw.config

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldNotContain

private const val DB_PASSWORD = "db-password-cleartext"
private const val SESSION_SECRET = "session-secret-cleartext"
private const val ADMIN_PASSWORD = "admin-password-cleartext"

private val SECRET_CLEARTEXTS = listOf(DB_PASSWORD, SESSION_SECRET, ADMIN_PASSWORD)

private val CONFIG =
    AppConfig(
        http = HttpConfig(host = "0.0.0.0", port = 8080),
        db =
            DbConfig(
                jdbcUrl = "jdbc:postgresql://localhost/cwfgw4k",
                user = "cwfgw4k",
                password = DB_PASSWORD,
                schema = "cwfgw4k",
                maxPoolSize = 10,
            ),
        auth =
            AuthConfig(
                sessionSecret = SESSION_SECRET,
                sessionMaxAgeSeconds = 86_400,
                adminUsername = "admin",
                adminPassword = ADMIN_PASSWORD,
                cookieSecure = true,
            ),
        cache = CacheConfig(defaultTtlSeconds = 300, sweepIntervalSeconds = 300),
        espn = EspnConfig(scoreboardCacheTtlSeconds = 300, scoreboardCacheMaxSize = 256),
        debug = DebugConfig(enabled = false),
    )

class ConfigSecretSpec : FunSpec({

    test("each config object holding one of the three secrets masks it in toString") {
        val rendered =
            mapOf(
                "AppConfig" to CONFIG.toString(),
                "DbConfig" to CONFIG.db.toString(),
                "AuthConfig" to CONFIG.auth.toString(),
            )

        rendered.forEach { (name, text) ->
            SECRET_CLEARTEXTS.forEach { cleartext ->
                withClue("$name.toString() = $text") { text shouldNotContain cleartext }
            }
        }
    }
})
