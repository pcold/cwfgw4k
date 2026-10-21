package com.cwfgw.cache

import com.cwfgw.testing.apiTest
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.seedCache
import io.kotest.assertions.nondeterministic.continually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

private const val STALE_KEY = "stale"
private const val LATER_STALE_KEY = "later-stale"
private val EXPIRED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")

// Long enough for a sweep launched at boot to have deleted the row, which is the regression this guards.
private val IDLE_WATCH = 500.milliseconds

class CacheSweepTrafficSpec : FunSpec({

    test("an app that serves no request never sweeps") {
        val rows = staleRows()
        apiTest(configure = { seedCache(rows) }) {
            startApplication()

            continually(IDLE_WATCH) { rows.storedKeys() shouldBe setOf(STALE_KEY) }
        }
    }

    test("the first request sweeps the expired rows before it is answered") {
        val rows = staleRows()
        apiTest(configure = { seedCache(rows) }) { client ->
            client.get("/api/v1/health")

            rows.storedKeys().shouldBeEmpty()
        }
    }

    test("a request outside the API does not sweep") {
        val rows = staleRows()
        apiTest(configure = { seedCache(rows) }) { client ->
            client.get("/")

            rows.storedKeys() shouldBe setOf(STALE_KEY)
        }
    }

    test("a request inside the interval after a sweep does not sweep again") {
        val rows = staleRows()
        apiTest(configure = { seedCache(rows) }) { client ->
            client.get("/api/v1/health")
            with(noopTransactionContext) { rows.put(LATER_STALE_KEY, "{}", EXPIRED_AT, CacheGeneration(0)) }

            client.get("/api/v1/health")

            rows.storedKeys() shouldBe setOf(LATER_STALE_KEY)
        }
    }
})

private fun staleRows(): FakeCacheRepository =
    FakeCacheRepository(initial = mapOf(STALE_KEY to FakeEntry(value = "{}", expiresAt = EXPIRED_AT)))
