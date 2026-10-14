package com.cwfgw.config

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.DoubleNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.decoder.DotPath
import com.sksamuel.hoplite.fp.Validated
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.reflect.typeOf

private const val SWEEP_INTERVAL_KEY = "cache.sweepIntervalSeconds"
private const val UNMISTAKABLE_NEGATIVE = "-7919"
private const val MILLIS_PER_SECOND = 1_000L
private const val FRACTIONAL_SECONDS = 1.5

private val SESSION_SECRET_SET: Pair<String, Any> = "auth.sessionSecret" to "session-secret"

private val TTL_VIOLATIONS: Map<String, ConfigViolation> =
    mapOf(
        "cache.defaultTtlSeconds" to ConfigViolation.RequestCacheTtlOutOfRange,
        "espn.scoreboardCacheTtlSeconds" to ConfigViolation.ScoreboardCacheTtlOutOfRange,
    )

private fun decode(node: Node): ConfigResult<PositiveSeconds> =
    PositiveSecondsDecoder().decode(node, typeOf<PositiveSeconds>(), DecoderContext.zero)

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

        PositiveSeconds.MAX.value * MILLIS_PER_SECOND shouldBeGreaterThan 0L
        beyond * MILLIS_PER_SECOND shouldBeLessThan 0L
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

    test("the loader accepts a sweep interval of one second") {
        val config = AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to "1", SESSION_SECRET_SET))

        config.cache.sweepIntervalSeconds.value shouldBe 1L
    }

    TTL_VIOLATIONS.forEach { (key, violation) ->
        listOf("0", UNMISTAKABLE_NEGATIVE, (PositiveSeconds.MAX.value + 1).toString()).forEach { raw ->
            test("the loader refuses a $key of $raw by the same bounds, naming the setting") {
                val failure =
                    shouldThrow<InvalidConfigException> { AppConfig.load(mapOf(key to raw, SESSION_SECRET_SET)) }

                failure.violations shouldContain violation
                failure.message shouldContain key
            }
        }

        test("the loader's refusal of a $key does not print the value it refused") {
            val failure =
                shouldThrow<InvalidConfigException> {
                    AppConfig.load(mapOf(key to UNMISTAKABLE_NEGATIVE, SESSION_SECRET_SET))
                }

            failure.message shouldNotContain UNMISTAKABLE_NEGATIVE
        }

        test("the loader accepts a $key of one second") {
            shouldNotThrowAny { AppConfig.load(mapOf(key to "1", SESSION_SECRET_SET)) }
        }
    }

    test("the decoder holds a numeric node to the same bounds as a string") {
        decode(LongNode(1L, Pos.NoPos, DotPath.root)).getUnsafe().value shouldBe 1L
        listOf(0L, Long.MAX_VALUE).forEach { seconds ->
            decode(LongNode(seconds, Pos.NoPos, DotPath.root)).shouldBeInstanceOf<Validated.Invalid<ConfigFailure>>()
        }
    }

    test("the decoder refuses a fractional number rather than truncating it") {
        decode(DoubleNode(FRACTIONAL_SECONDS, Pos.NoPos, DotPath.root))
            .shouldBeInstanceOf<Validated.Invalid<ConfigFailure>>()
    }
})
