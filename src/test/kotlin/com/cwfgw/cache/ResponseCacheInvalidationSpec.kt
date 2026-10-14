package com.cwfgw.cache

import ch.qos.logback.classic.Level
import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.Season
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.captureLogs
import com.cwfgw.testing.regularUserApiTest
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.plugin
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingRoot
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val CACHE_REVISION = "invalidation-test"
private const val SEEDED_KEY = "$CACHE_REVISION /api/v1/seasons/any/rankings"
private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000108"))

private data class WriteCase(
    val description: String,
    val expectedStatus: HttpStatusCode,
    val perform: suspend (client: HttpClient, season: Season) -> HttpResponse,
)

// One per way a handler sets its status: on the response, nowhere, and on a status-only body.
private val WRITE_CASES =
    listOf(
        WriteCase("POST /leagues", HttpStatusCode.Created) { client, _ ->
            client.post("/api/v1/leagues") {
                contentType(ContentType.Application.Json)
                setBody(CreateLeagueRequest(name = "Castlewood"))
            }
        },
        WriteCase("POST /seasons/{sid}/clean-results", HttpStatusCode.OK) { client, season ->
            client.post("/api/v1/seasons/${season.id.value}/clean-results")
        },
        WriteCase("DELETE /seasons/{id}", HttpStatusCode.NoContent) { client, season ->
            client.delete("/api/v1/seasons/${season.id.value}")
        },
    )

/** A clear that fails as jOOQ reports a failed statement; everything else is [FakeCacheRepository]. */
private class FailingClearRepository(private val rows: FakeCacheRepository) : CacheRepository {
    context(ctx: TransactionContext)
    override fun get(key: String): String? = rows.get(key)

    context(ctx: TransactionContext)
    override fun generation(): CacheGeneration = rows.generation()

    context(ctx: WriteTransactionContext)
    override fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean = rows.put(key, value, expiresAt, generation)

    context(ctx: WriteTransactionContext)
    override fun deleteExpired(): Int = rows.deleteExpired()

    context(ctx: WriteTransactionContext)
    override fun clear(): Int = throw DataAccessException("SQL [delete from cache]; ERROR", SQLException("ERROR"))
}

/** Runs [onClear] inside the clear's transaction, before the rows go. */
private class ObservedClearRepository(
    private val rows: FakeCacheRepository,
    private val onClear: context(TransactionContext) () -> Unit,
) : CacheRepository {
    context(ctx: TransactionContext)
    override fun get(key: String): String? = rows.get(key)

    context(ctx: TransactionContext)
    override fun generation(): CacheGeneration = rows.generation()

    context(ctx: WriteTransactionContext)
    override fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean = rows.put(key, value, expiresAt, generation)

    context(ctx: WriteTransactionContext)
    override fun deleteExpired(): Int = rows.deleteExpired()

    context(ctx: WriteTransactionContext)
    override fun clear(): Int {
        onClear()
        return rows.clear()
    }
}

private fun seededRows(): FakeCacheRepository =
    FakeCacheRepository(
        initial =
            mapOf(SEEDED_KEY to FakeEntry(value = "[]", expiresAt = Instant.now().plusSeconds(300))),
    )

private fun ApiFixture.cacheOver(rows: CacheRepository) {
    requestCache = RequestCache(rows, transactor, CACHE_REVISION, defaultTtl = Duration.ofMinutes(5))
}

private suspend fun HttpClient.createSeason(): Season =
    post("/api/v1/seasons") {
        contentType(ContentType.Application.Json)
        setBody(CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026))
    }.body()

private fun Route.descendants(): Sequence<Route> =
    sequenceOf(this) + children.asSequence().flatMap { it.descendants() }

private fun Route.ancestors(): Sequence<Route> = generateSequence(this) { it.parent }

class ResponseCacheInvalidationSpec : FunSpec({

    test("every write route the app mounts sits under /api/v1, where the clear is installed") {
        apiTest { _ ->
            startApplication()
            val root = application.plugin(RoutingRoot)
            val api = root.descendants().singleOrNull { it.toString() == "/api/v1" }.shouldNotBeNull()
            val writes =
                root.descendants().filter { node ->
                    val selector = node.selector
                    selector is HttpMethodRouteSelector && selector.method !in READ_METHODS
                }.toList()

            writes.shouldNotBeEmpty()
            withClue("writes mounted outside /api/v1") {
                writes.filterNot { api in it.ancestors() }.map(Route::toString).shouldBeEmpty()
            }
        }
    }

    test("a season deleted through the API is no longer served from its cached rankings") {
        authenticatedApiTest { client ->
            val season = client.createSeason()
            val rankings = "/api/v1/seasons/${season.id.value}/rankings"

            client.get(rankings).status shouldBe HttpStatusCode.OK
            client.delete("/api/v1/seasons/${season.id.value}").status shouldBe HttpStatusCode.NoContent

            client.get(rankings).status shouldBe HttpStatusCode.NotFound
        }
    }

    WRITE_CASES.forEach { case ->
        test("a successful admin ${case.description} empties the cache before answering ${case.expectedStatus}") {
            val rows = FakeCacheRepository()
            authenticatedApiTest(configure = { cacheOver(rows) }) { client ->
                val season = client.createSeason()
                client.get("/api/v1/seasons/${season.id.value}/rankings").status shouldBe HttpStatusCode.OK
                rows.storedKeys().shouldNotBeEmpty()

                case.perform(client, season).status shouldBe case.expectedStatus

                rows.storedKeys().shouldBeEmpty()
            }
        }
    }

    test("the clear runs once the write has landed, so a read it lets through cannot see the pre-write rows") {
        val seasonsAtClear = mutableListOf<List<Season>>()
        authenticatedApiTest(
            configure = {
                cacheOver(
                    ObservedClearRepository(FakeCacheRepository()) {
                        seasonsAtClear += seasonRepository.findAll(leagueId = null, seasonYear = null)
                    },
                )
            },
        ) { client ->
            val season = client.createSeason()
            seasonsAtClear.clear()

            client.delete("/api/v1/seasons/${season.id.value}").status shouldBe HttpStatusCode.NoContent

            seasonsAtClear shouldBe listOf(emptyList())
        }
    }

    test("an admin write answered with an error still empties the cache, since an error can follow a commit") {
        val rows = seededRows()
        authenticatedApiTest(configure = { cacheOver(rows) }) { client ->
            client.delete("/api/v1/seasons/${UUID.randomUUID()}").status shouldBe HttpStatusCode.NotFound

            rows.storedKeys().shouldBeEmpty()
        }
    }

    test("a write refused to a non-admin leaves the cache alone") {
        val rows = seededRows()
        regularUserApiTest(configure = { cacheOver(rows) }) { client ->
            val response =
                client.post("/api/v1/leagues") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateLeagueRequest(name = "Castlewood"))
                }

            response.status shouldBe HttpStatusCode.Forbidden
            rows.storedKeys() shouldBe setOf(SEEDED_KEY)
        }
    }

    test("an anonymous logout answers 204 without clearing the cache") {
        val rows = seededRows()
        apiTest(configure = { cacheOver(rows) }) { client ->
            client.post("/api/v1/auth/logout").status shouldBe HttpStatusCode.NoContent

            rows.storedKeys() shouldBe setOf(SEEDED_KEY)
        }
    }

    test("a clear that fails still answers the committed write's success, logging it at WARN") {
        captureLogs { logs ->
            authenticatedApiTest(configure = { cacheOver(FailingClearRepository(seededRows())) }) { client ->
                val response =
                    client.post("/api/v1/leagues") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateLeagueRequest(name = "Castlewood"))
                    }

                response.status shouldBe HttpStatusCode.Created
            }

            logs.events().filter { it.formattedMessage == "cwfgw4k.cache event=uncleared" }
                .map { it.level } shouldBe listOf(Level.WARN)
        }
    }
})
