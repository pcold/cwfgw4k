package com.cwfgw.config

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val SWEEP_INTERVAL_KEY = "cache.sweepIntervalSeconds"
private const val UNMISTAKABLE_NEGATIVE = "-7919"
private const val MILLIS_PER_SECOND = 1_000L

class PositiveSecondsSpec : FunSpec({

    listOf(0L, -1L, Long.MIN_VALUE, PositiveSeconds.MAX.value + 1, Long.MAX_VALUE).forEach { seconds ->
        test("$seconds seconds is refused") {
            PositiveSeconds.of(seconds).shouldBeNull()
        }
    }

    listOf(1L, PositiveSeconds.MAX.value).forEach { seconds ->
        test("$seconds seconds is accepted and held unchanged") {
            PositiveSeconds.of(seconds)?.value shouldBe seconds
        }
    }

    test("the largest value is the last whose count in milliseconds does not wrap negative") {
        val beyond = PositiveSeconds.MAX.value + 1

        (PositiveSeconds.MAX.value * MILLIS_PER_SECOND > 0) shouldBe true
        (beyond * MILLIS_PER_SECOND < 0) shouldBe true
    }

    listOf("0", "-1", Long.MAX_VALUE.toString(), "5m").forEach { raw ->
        test("the loader refuses a sweep interval of $raw, naming the setting") {
            val failure = shouldThrow<ConfigException> { AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to raw)) }

            failure.message shouldContain "sweepIntervalSeconds"
        }
    }

    test("the loader's refusal prints the value it refused") {
        val failure =
            shouldThrow<ConfigException> { AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to UNMISTAKABLE_NEGATIVE)) }

        failure.message shouldContain UNMISTAKABLE_NEGATIVE
    }

    test("the loader refuses a numeric zero as it does the string an environment variable arrives as") {
        shouldThrow<ConfigException> { AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to 0L)) }
    }

    listOf<Any>("1", 1L).forEach { raw ->
        test("the loader accepts a sweep interval of one second given as a ${raw::class.simpleName}") {
            AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to raw)).cache.sweepIntervalSeconds.value shouldBe 1L
        }
    }
})
