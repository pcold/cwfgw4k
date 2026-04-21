package com.cwfgw.leagues

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.HealthProbe
import com.cwfgw.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.Instant
import java.util.UUID

private val CASTLEWOOD_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
private val CASTLEWOOD_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z")
private val CASTLEWOOD_SEED =
    League(id = CASTLEWOOD_ID, name = "Castlewood Fantasy Golf", createdAt = CASTLEWOOD_CREATED_AT)

private val ALWAYS_HEALTHY = HealthProbe { true }

@OptIn(ExperimentalSerializationApi::class)
private val testJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

class LeagueRoutesSpec : FunSpec({

    test("GET /api/v1/leagues returns the seeded leagues") {
        withLeagueApp(seeded = listOf(CASTLEWOOD_SEED)) { client ->
            val response = client.get("/api/v1/leagues")

            response.status shouldBe HttpStatusCode.OK
            val leagues: List<League> = response.body()
            leagues.map { it.name } shouldContain "Castlewood Fantasy Golf"
        }
    }

    test("GET /api/v1/leagues/{id} returns the league with 200") {
        withLeagueApp(seeded = listOf(CASTLEWOOD_SEED)) { client ->
            val response = client.get("/api/v1/leagues/${CASTLEWOOD_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            val league: League = response.body()
            league.id shouldBe CASTLEWOOD_ID
            league.name shouldBe "Castlewood Fantasy Golf"
        }
    }

    test("GET /api/v1/leagues/{unknown-uuid} returns 404") {
        withLeagueApp(seeded = listOf(CASTLEWOOD_SEED)) { client ->
            val response = client.get("/api/v1/leagues/${UUID.randomUUID()}")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/leagues/{non-uuid} returns 400") {
        withLeagueApp { client ->
            val response = client.get("/api/v1/leagues/not-a-uuid")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/leagues creates the league and exposes it via GET") {
        val newId = LeagueId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val newCreatedAt = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeLeagueRepository(idFactory = { newId }, clock = { newCreatedAt })

        withLeagueApp(fake = fake) { client ->
            val createResponse =
                client.post("/api/v1/leagues") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateLeagueRequest(name = "PGA Classic 2026"))
                }

            createResponse.status shouldBe HttpStatusCode.Created
            val created: League = createResponse.body()
            created.id shouldBe newId
            created.name shouldBe "PGA Classic 2026"
            created.createdAt shouldBe newCreatedAt

            val getResponse = client.get("/api/v1/leagues/${newId.value}")
            getResponse.status shouldBe HttpStatusCode.OK
            getResponse.body<League>() shouldBe created
        }
    }
})

private suspend fun withLeagueApp(
    seeded: List<League> = emptyList(),
    fake: FakeLeagueRepository = FakeLeagueRepository(initial = seeded),
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) {
    testApplication {
        application {
            module(
                healthProbe = ALWAYS_HEALTHY,
                leagueService = LeagueService(fake),
                golferService = GolferService(FakeGolferRepository()),
            )
        }
        val client = clientWithJson()
        block(client)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun ApplicationTestBuilder.clientWithJson() =
    createClient {
        install(ContentNegotiation) {
            json(testJson)
        }
    }
