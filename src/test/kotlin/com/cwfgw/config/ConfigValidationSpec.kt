package com.cwfgw.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val SESSION_SECRET = "session-secret-cleartext"
private const val ADMIN_USERNAME = "admin-username-cleartext"
private const val ADMIN_PASSWORD = "admin-password-cleartext"
private const val DB_PASSWORD = "db-password-cleartext"
private const val UNMISTAKABLE_POOL_SIZE = "-7919"

private val CLEARTEXTS = listOf(SESSION_SECRET, ADMIN_USERNAME, ADMIN_PASSWORD, DB_PASSWORD)

/** Every key the rules read is pinned here, so an exported AUTH_ADMIN_USERNAME cannot change a case's outcome. */
private fun validOverrides(): Map<String, Any> =
    mapOf(
        "auth.sessionSecret" to SESSION_SECRET,
        "auth.adminUsername" to "",
        "auth.adminPassword" to "",
        "db.password" to DB_PASSWORD,
    )

private fun refusal(vararg changes: Pair<String, Any>): InvalidConfigException =
    shouldThrow<InvalidConfigException> { AppConfig.load(validOverrides() + changes) }

private fun InvalidConfigException.shouldNotPrintAnyValue() {
    val text = message.orEmpty()
    (CLEARTEXTS + UNMISTAKABLE_POOL_SIZE).forEach { value -> text shouldNotContain value }
}

class ConfigValidationSpec : FunSpec({

    test("a fully valid config loads") {
        AppConfig.load(validOverrides()).auth.sessionSecret.cleartext shouldBe SESSION_SECRET
    }

    test("a blank session secret is refused, naming the key") {
        val failure = refusal("auth.sessionSecret" to "")

        failure.violations shouldContainExactly listOf(ConfigViolation.BlankSessionSecret)
        failure.message shouldContain "auth.sessionSecret"
        failure.shouldNotPrintAnyValue()
    }

    test("an admin username without a password is refused, naming the missing password") {
        val failure = refusal("auth.adminUsername" to ADMIN_USERNAME)

        failure.violations shouldContainExactly listOf(ConfigViolation.AdminUsernameWithoutPassword)
        failure.message shouldContain "auth.adminPassword"
        failure.shouldNotPrintAnyValue()
    }

    test("an admin password without a username is refused without printing the password") {
        val failure = refusal("auth.adminPassword" to ADMIN_PASSWORD)

        failure.violations shouldContainExactly listOf(ConfigViolation.AdminPasswordWithoutUsername)
        failure.message shouldContain "auth.adminUsername"
        failure.shouldNotPrintAnyValue()
    }

    test("a whitespace-only admin password counts as unset") {
        refusal("auth.adminUsername" to ADMIN_USERNAME, "auth.adminPassword" to "   ").violations shouldContainExactly
            listOf(ConfigViolation.AdminUsernameWithoutPassword)
    }

    test("an admin with both halves set loads") {
        val fullAdmin = mapOf("auth.adminUsername" to ADMIN_USERNAME, "auth.adminPassword" to ADMIN_PASSWORD)

        val config = AppConfig.load(validOverrides() + fullAdmin)

        config.auth.adminPassword?.cleartext shouldBe ADMIN_PASSWORD
    }

    listOf("0", UNMISTAKABLE_POOL_SIZE).forEach { raw ->
        test("a pool size of $raw is refused, naming the key and not the value") {
            val failure = refusal("db.maxPoolSize" to raw)

            failure.violations shouldContainExactly listOf(ConfigViolation.PoolSizeNotPositive)
            failure.message shouldContain "db.maxPoolSize"
            failure.shouldNotPrintAnyValue()
        }
    }

    test("every violation is listed in one refusal, each by key and none by value") {
        val failure =
            refusal(
                "auth.sessionSecret" to "",
                "auth.adminPassword" to ADMIN_PASSWORD,
                "db.maxPoolSize" to UNMISTAKABLE_POOL_SIZE,
                "cache.defaultTtlSeconds" to "0",
            )

        failure.violations shouldContainExactlyInAnyOrder
            listOf(
                ConfigViolation.BlankSessionSecret,
                ConfigViolation.AdminPasswordWithoutUsername,
                ConfigViolation.PoolSizeNotPositive,
                ConfigViolation.RequestCacheTtlOutOfRange,
            )
        failure.violations.forEach { violation -> failure.message shouldContain violation.key }
        failure.shouldNotPrintAnyValue()
    }

    test("the seed load skips the auth rules") {
        val noSecretHalfAdmin = mapOf("auth.sessionSecret" to "", "auth.adminUsername" to ADMIN_USERNAME)

        val config = AppConfig.loadForSeed(validOverrides() + noSecretHalfAdmin)

        config.auth.adminUsername shouldBe ADMIN_USERNAME
    }

    test("the seed load still refuses a pool size it cannot open") {
        shouldThrow<InvalidConfigException> {
            AppConfig.loadForSeed(validOverrides() + ("db.maxPoolSize" to "0"))
        }.violations shouldContainExactly listOf(ConfigViolation.PoolSizeNotPositive)
    }
})
