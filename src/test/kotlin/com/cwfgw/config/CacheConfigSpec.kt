package com.cwfgw.config

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SWEEP_INTERVAL_KEY = "cache.sweepIntervalSeconds"

class CacheConfigSpec : FunSpec({

    listOf(0L, -1L).forEach { seconds ->
        test("a sweep interval of $seconds is refused at load, naming the setting and the value") {
            val failure = shouldThrow<ConfigException> { AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to seconds)) }

            failure.message shouldBe
                "cache.sweepIntervalSeconds (CACHE_SWEEP_INTERVAL_SECONDS) must be positive, was $seconds"
        }
    }

    test("a sweep interval of one second loads") {
        AppConfig.load(mapOf(SWEEP_INTERVAL_KEY to 1L)).cache.sweepIntervalSeconds shouldBe 1L
    }
})
