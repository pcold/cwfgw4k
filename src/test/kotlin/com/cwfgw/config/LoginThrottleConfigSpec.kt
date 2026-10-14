package com.cwfgw.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val UNMISTAKABLE_MAX_FAILURES = "-7919"

private val SESSION_SECRET_SET: Pair<String, Any> = "auth.sessionSecret" to "session-secret"

class LoginThrottleConfigSpec : FunSpec({

    test("the shipped defaults allow five failures in a fifteen-minute window") {
        val config = AppConfig.load(mapOf(SESSION_SECRET_SET))

        config.loginThrottle shouldBe LoginThrottleConfig(maxFailures = 5, windowSeconds = 900)
    }

    listOf("0", UNMISTAKABLE_MAX_FAILURES).forEach { raw ->
        test("a maxFailures of $raw is refused, naming the key and not the value") {
            val failure =
                shouldThrow<InvalidConfigException> {
                    AppConfig.load(mapOf(SESSION_SECRET_SET, "loginThrottle.maxFailures" to raw))
                }

            failure.violations shouldContainExactly listOf(ConfigViolation.LoginThrottleMaxFailuresNotPositive)
            failure.message shouldContain "loginThrottle.maxFailures"
            failure.message shouldNotContain UNMISTAKABLE_MAX_FAILURES
        }
    }

    test("the seed load refuses a throttle it could not build, since it builds the same services") {
        shouldThrow<InvalidConfigException> {
            AppConfig.loadForSeed(mapOf("loginThrottle.maxFailures" to "0"))
        }.violations shouldContainExactly listOf(ConfigViolation.LoginThrottleMaxFailuresNotPositive)
    }
})
