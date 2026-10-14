package com.cwfgw.cache

import ch.qos.logback.classic.Level
import com.cwfgw.db.LOCK_NOT_AVAILABLE
import com.cwfgw.db.TransactionContext
import com.cwfgw.http.installErrorHandling
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.captureLogs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

private const val TEST_KEY = "/api/v1/scoring/abc/def"
private const val TEST_ROUTE = "/api/v1/scoring/:id/:id"
private const val TEST_REVISION = "cwfgw4k-00041-old"
private const val NEXT_REVISION = "cwfgw4k-00042-new"

// Brief pause to let all concurrent callers register on the in-flight map
// before the gated fetch resolves.
private const val SETTLE_MS: Long = 50

private class StepClock(start: Instant = Instant.parse("2026-05-03T00:00:00Z")) : Clock() {
    private var current: Instant = start

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun instant(): Instant = current

    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: ZoneId) = error("not used in tests")
}

/** Every put fails as jOOQ reports a failed statement, with [sqlState] on the wrapped driver exception. */
private class FailingPutRepository(private val sqlState: String) : CacheRepository {
    context(ctx: TransactionContext)
    override fun get(key: String): String? = null

    context(ctx: TransactionContext)
    override fun generation(): CacheGeneration = CacheGeneration(0)

    context(ctx: TransactionContext)
    override fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean = throw DataAccessException("SQL [insert into cache ...]; ERROR", SQLException("ERROR", sqlState))

    context(ctx: TransactionContext)
    override fun deleteExpired(): Int = 0

    context(ctx: TransactionContext)
    override fun clear(): Int = 0
}

class RequestCacheSpec : FunSpec({

    test("cachedJsonGet returns the cached value on hit and only invokes fetch once") {
        val cache =
            RequestCache(FakeCacheRepository(), FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofMinutes(5))
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
        val cache =
            RequestCache(FakeCacheRepository(), FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofMinutes(5))
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
        val cache =
            RequestCache(repo, FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofSeconds(60), clock = clock)
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

    test("concurrent misses for the same key coalesce to a single fetch (single-flight)") {
        val cache =
            RequestCache(FakeCacheRepository(), FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofMinutes(5))
        val gate = CompletableDeferred<String>()
        val fetchStarted = AtomicInteger(0)
        val fetch: suspend () -> String = {
            fetchStarted.incrementAndGet()
            gate.await()
        }

        val results =
            coroutineScope {
                val concurrent = (1..10).map { async { cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch) } }
                // Give the in-flight registration time to settle for all 10
                // callers before we resolve the gate. With single-flight only
                // the first reaches `fetch`; the others coalesce.
                Thread.sleep(SETTLE_MS)
                gate.complete("[\"shared\"]")
                concurrent.awaitAll()
            }

        results.size shouldBe 10
        results.forEach { it shouldBe "[\"shared\"]" }
        fetchStarted.get() shouldBe 1
    }

    test("a body one revision stored is refetched by the next revision and still a hit for the first") {
        val sharedRows = FakeCacheRepository()
        val ttl = Duration.ofMinutes(5)
        val writer = RequestCache(sharedRows, FakeTransactor(), TEST_REVISION, defaultTtl = ttl)
        val nextRevision = RequestCache(sharedRows, FakeTransactor(), NEXT_REVISION, defaultTtl = ttl)
        val sameRevision = RequestCache(sharedRows, FakeTransactor(), TEST_REVISION, defaultTtl = ttl)

        writer.cachedJsonGet(TEST_KEY, TEST_ROUTE) { "[\"old shape\"]" } shouldBe "[\"old shape\"]"
        nextRevision.cachedJsonGet(TEST_KEY, TEST_ROUTE) { "[\"new shape\"]" } shouldBe "[\"new shape\"]"
        sameRevision.cachedJsonGet(TEST_KEY, TEST_ROUTE) { "[\"refetched\"]" } shouldBe "[\"old shape\"]"
    }

    test("deleteExpired purges only the entries past their expiry") {
        val clock = StepClock()
        val repo = FakeCacheRepository(clock = clock)
        val cache =
            RequestCache(repo, FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofSeconds(60), clock = clock)

        cache.cachedJsonGet("/api/v1/keep", "/api/v1/keep", ttl = Duration.ofSeconds(120)) { "ok" }
        cache.cachedJsonGet("/api/v1/drop", "/api/v1/drop", ttl = Duration.ofSeconds(30)) { "ok" }

        clock.advance(Duration.ofSeconds(60))
        val deleted = cache.deleteExpired()

        deleted shouldBe 1
    }

    test("a cached GET whose put waits out the lock timeout answers 200 with its body, logging the skip at WARN") {
        val cache =
            RequestCache(
                FailingPutRepository(sqlState = LOCK_NOT_AVAILABLE),
                FakeTransactor(),
                TEST_REVISION,
                defaultTtl = Duration.ofMinutes(5),
            )

        captureLogs { logs ->
            testApplication {
                application {
                    install(ContentNegotiation) { json(Json) }
                    installErrorHandling()
                    routing { get(TEST_KEY) { cachedRespond(cache) { listOf("fresh") } } }
                }
                val response = client.get(TEST_KEY)

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe """["fresh"]"""
            }

            logs.events().filter { it.formattedMessage.startsWith("cwfgw4k.cache event=unstored ") }
                .map { it.level } shouldBe listOf(Level.WARN)
            logs.events().filter { it.level == Level.ERROR }.shouldBeEmpty()
        }
    }

    test("a put that fails for any other reason still fails the GET") {
        val cache =
            RequestCache(
                FailingPutRepository(sqlState = "23505"),
                FakeTransactor(),
                TEST_REVISION,
                defaultTtl = Duration.ofMinutes(5),
            )

        shouldThrow<DataAccessException> {
            cache.cachedJsonGet(TEST_KEY, TEST_ROUTE) { "[\"fresh\"]" }
        }
    }

    test("clear drops every stored body, so the next read fetches again") {
        val cache =
            RequestCache(FakeCacheRepository(), FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofMinutes(5))
        val calls = AtomicInteger(0)
        val fetch: suspend () -> String = { "[\"${calls.incrementAndGet()}\"]" }

        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch) shouldBe "[\"1\"]"
        cache.clear() shouldBe 1
        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE, fetch = fetch) shouldBe "[\"2\"]"
    }

    test("a fill whose fetch straddles a clear answers its caller but stores nothing") {
        val rows = FakeCacheRepository()
        val cache = RequestCache(rows, FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofMinutes(5))
        val fetchStarted = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<String>()

        coroutineScope {
            val straddling =
                async {
                    cache.cachedJsonGet(TEST_KEY, TEST_ROUTE) {
                        fetchStarted.complete(Unit)
                        gate.await()
                    }
                }
            fetchStarted.await()
            cache.clear()
            gate.complete("[\"pre-write\"]")

            straddling.await() shouldBe "[\"pre-write\"]"
        }
        rows.storedKeys().shouldBeEmpty()
    }

    test("a read after a clear fetches for itself instead of joining a fill that began before it") {
        val cache =
            RequestCache(FakeCacheRepository(), FakeTransactor(), TEST_REVISION, defaultTtl = Duration.ofMinutes(5))
        val fetchStarted = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<String>()

        coroutineScope {
            val straddling =
                async {
                    cache.cachedJsonGet(TEST_KEY, TEST_ROUTE) {
                        fetchStarted.complete(Unit)
                        gate.await()
                    }
                }
            fetchStarted.await()
            cache.clear()
            val afterClear =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.cachedJsonGet(TEST_KEY, TEST_ROUTE) { "[\"post-write\"]" }
                }
            gate.complete("[\"pre-write\"]")

            afterClear.await() shouldBe "[\"post-write\"]"
            straddling.await() shouldBe "[\"pre-write\"]"
        }
        cache.cachedJsonGet(TEST_KEY, TEST_ROUTE) { "[\"refetched\"]" } shouldBe "[\"post-write\"]"
    }
})
