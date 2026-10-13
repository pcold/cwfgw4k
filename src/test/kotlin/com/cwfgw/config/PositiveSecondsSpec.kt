package com.cwfgw.config

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.DoubleNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.decoder.DotPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.reflect.typeOf

private const val SWEEP_INTERVAL_KEY = "cache.sweepIntervalSeconds"
private const val UNMISTAKABLE_NEGATIVE = "-7919"
private const val MILLIS_PER_SECOND = 1_000L
private const val FRACTIONAL_SECONDS = 1.5

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

    test("the loader accepts a sweep interval of one second") {
        AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to "1")).cache.sweepIntervalSeconds.value shouldBe 1L
    }

    test("the decoder holds a numeric node to the same bounds as a string") {
        decode(LongNode(1L, Pos.NoPos, DotPath.root)).getUnsafe().value shouldBe 1L
        decode(LongNode(0L, Pos.NoPos, DotPath.root)).isInvalid() shouldBe true
    }

    test("the decoder refuses a fractional number rather than truncating it") {
        decode(DoubleNode(FRACTIONAL_SECONDS, Pos.NoPos, DotPath.root)).isInvalid() shouldBe true
    }
})
