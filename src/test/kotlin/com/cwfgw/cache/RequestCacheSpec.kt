package com.cwfgw.cache

import com.cwfgw.testing.FakeTransactor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

private const val TEST_KEY = "/api/v1/scoring/abc/def"
private const val TEST_ROUTE = "/api/v1/scoring/:id/:id"

private class StepClock(start: Instant = Instant.parse("2026-05-03T00:00:00Z")) : Clock() {
    private var current: Instant = start

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun instant(): Instant = current

    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId) = error("not used in tests")
}

class RequestCacheSpec : FunSpec({

    test("cachedJsonGet returns the cached value on hit and only invokes fetch once") {
        val cache = RequestCache(FakeCacheRepository(), FakeTransactor(), defaultTtl = Duration.ofMinutes(5))
        val calls = AtomicInteger(0)
        val fetch: suspend () -> String = {
            calls.incrementAndGet()
            "[\"first\"]"
        }

        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch) shouldBe "[\"first\"]"
        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch) shouldBe "[\"first\"]"

        calls.get() shouldBe 1
    }

    test("a fetch failure does not store an entry — the next call retries") {
        val cache = RequestCache(FakeCacheRepository(), FakeTransactor(), defaultTtl = Duration.ofMinutes(5))
        val calls = AtomicInteger(0)
        val fetch: suspend () -> String = {
            if (calls.incrementAndGet() == 1) error("upstream broke") else "[\"recovered\"]"
        }

        shouldThrow<IllegalStateException> {
            cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch)
        }
        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch) shouldBe "[\"recovered\"]"

        calls.get() shouldBe 2
    }

    test("entries beyond TTL are not returned and the fetch runs again") {
        val clock = StepClock()
        val repo = FakeCacheRepository(clock = clock)
        val cache = RequestCache(repo, FakeTransactor(), defaultTtl = Duration.ofSeconds(60), clock = clock)
        val calls = AtomicInteger(0)
        val fetch: suspend () -> String = {
            calls.incrementAndGet()
            "[\"$\\${calls.get()}\"]"
        }

        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch)
        clock.advance(Duration.ofSeconds(61))
        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch)

        calls.get() shouldBe 2
    }

    test("deleteExpired purges only the entries past their expiry") {
        val clock = StepClock()
        val repo = FakeCacheRepository(clock = clock)
        val cache = RequestCache(repo, FakeTransactor(), defaultTtl = Duration.ofSeconds(60), clock = clock)

        cache.cachedJsonGet("/api/v1/keep", "/api/v1/keep", ttl = Duration.ofSeconds(120)) { "ok" }
        cache.cachedJsonGet("/api/v1/drop", "/api/v1/drop", ttl = Duration.ofSeconds(30)) { "ok" }

        clock.advance(Duration.ofSeconds(60))
        val deleted = cache.deleteExpired()

        deleted shouldBe 1
    }
})
