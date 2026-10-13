package com.cwfgw.config

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val DB_PASSWORD = "db-password-cleartext"
private const val SESSION_SECRET = "session-secret-cleartext"
private const val ADMIN_PASSWORD = "admin-password-cleartext"
private const val NESTED_CLEARTEXT = "nested-cleartext"
private const val NUMERIC_PASSWORD = 90_210_555L

private val SECRET_CLEARTEXTS = listOf(DB_PASSWORD, SESSION_SECRET, ADMIN_PASSWORD)

private val CONFIG =
    AppConfig(
        http = HttpConfig(host = "0.0.0.0", port = 8080),
        db =
            DbConfig(
                jdbcUrl = "jdbc:postgresql://localhost/cwfgw4k",
                user = "cwfgw4k",
                password = ConfigSecret(DB_PASSWORD),
                schema = "cwfgw4k",
                maxPoolSize = 10,
            ),
        auth =
            AuthConfig(
                sessionSecret = ConfigSecret(SESSION_SECRET),
                sessionMaxAgeSeconds = 86_400,
                adminUsername = "admin",
                adminPassword = ConfigSecret(ADMIN_PASSWORD),
                cookieSecure = true,
            ),
        cache = CacheConfig(defaultTtlSeconds = 300, sweepIntervalSeconds = 300),
        espn = EspnConfig(scoreboardCacheTtlSeconds = 300, scoreboardCacheMaxSize = 256),
        debug = DebugConfig(enabled = false),
    )

private fun everySecretOverride(): Map<String, Any> =
    mapOf(
        "db.password" to DB_PASSWORD,
        "auth.sessionSecret" to SESSION_SECRET,
        "auth.adminPassword" to ADMIN_PASSWORD,
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
            text shouldContain "****"
        }
    }

    test("a secret interpolated into a message is masked") {
        val message = "boot failed with ${ConfigSecret(SESSION_SECRET)}"

        message shouldBe "boot failed with ****"
    }

    test("a blank secret is masked the same as a set one") {
        ConfigSecret("").toString() shouldBe ConfigSecret(SESSION_SECRET).toString()
    }

    test("cleartext is the value the secret was built from") {
        ConfigSecret(SESSION_SECRET).cleartext shouldBe SESSION_SECRET
    }

    test("the loader decodes every secret field from a string") {
        val config = AppConfig.load(everySecretOverride())

        config.db.password.cleartext shouldBe DB_PASSWORD
        config.auth.sessionSecret.cleartext shouldBe SESSION_SECRET
        config.auth.adminPassword?.cleartext shouldBe ADMIN_PASSWORD
    }

    test("the loader decodes a blank string to a blank secret rather than refusing it or reading it as absent") {
        val config = AppConfig.load(mapOf("auth.sessionSecret" to "", "auth.adminPassword" to ""))

        config.auth.sessionSecret.cleartext shouldBe ""
        config.auth.adminPassword?.cleartext shouldBe ""
    }

    test("the loader decodes a numeric secret to its digits") {
        val config = AppConfig.load(mapOf("db.password" to NUMERIC_PASSWORD))

        config.db.password.cleartext shouldBe NUMERIC_PASSWORD.toString()
    }

    test("a nested node under a secret key fails boot without printing its contents") {
        val failure =
            shouldThrow<ConfigException> {
                AppConfig.load(mapOf("db.password.nested" to NESTED_CLEARTEXT))
            }

        failure.message shouldContain "password"
        failure.message shouldNotContain NESTED_CLEARTEXT
    }
})
