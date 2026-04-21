package com.cwfgw.seasons

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.HealthProbe
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueService
import com.cwfgw.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val ALWAYS_HEALTHY = HealthProbe { true }

private val CASTLEWOOD_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

private fun season(
    id: SeasonId = SeasonId(UUID.randomUUID()),
    leagueId: LeagueId = CASTLEWOOD_ID,
    name: String = "2026 Season",
    seasonYear: Int = 2026,
    seasonNumber: Int = 1,
): Season =
    Season(
        id = id,
        leagueId = leagueId,
        name = name,
        seasonYear = seasonYear,
        seasonNumber = seasonNumber,
        status = "draft",
        tieFloor = BigDecimal.ONE,
        sideBetAmount = BigDecimal(15),
        maxTeams = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

@OptIn(ExperimentalSerializationApi::class)
private val testJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

class SeasonRoutesSpec : FunSpec({

    test("GET /api/v1/seasons returns seeded seasons") {
        val s = season()
        withSeasonApp(seeded = listOf(s)) { client ->
            val response = client.get("/api/v1/seasons")

            response.status shouldBe HttpStatusCode.OK
            val seasons: List<Season> = response.body()
            seasons.map { it.id } shouldContainExactly listOf(s.id)
        }
    }

    test("GET /api/v1/seasons?league_id filters by league") {
        val otherLeague = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000009"))
        val castlewoodSeason = season(leagueId = CASTLEWOOD_ID)
        val otherSeason = season(leagueId = otherLeague, name = "Other")

        withSeasonApp(seeded = listOf(castlewoodSeason, otherSeason)) { client ->
            val response = client.get("/api/v1/seasons?league_id=${CASTLEWOOD_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            val seasons: List<Season> = response.body()
            seasons.map { it.id } shouldContainExactly listOf(castlewoodSeason.id)
        }
    }

    test("GET /api/v1/seasons?year filters by season_year") {
        val y2025 = season(name = "2025", seasonYear = 2025)
        val y2026 = season(name = "2026", seasonYear = 2026)

        withSeasonApp(seeded = listOf(y2025, y2026)) { client ->
            val response = client.get("/api/v1/seasons?year=2025")

            val seasons: List<Season> = response.body()
            seasons.map { it.id } shouldContainExactly listOf(y2025.id)
        }
    }

    test("GET /api/v1/seasons/{id} returns 200 with the season") {
        val s = season()
        withSeasonApp(seeded = listOf(s)) { client ->
            val response = client.get("/api/v1/seasons/${s.id.value}")

            response.status shouldBe HttpStatusCode.OK
            response.body<Season>() shouldBe s
        }
    }

    test("GET /api/v1/seasons/{unknown-uuid} returns 404") {
        withSeasonApp { client ->
            val response = client.get("/api/v1/seasons/${UUID.randomUUID()}")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/seasons/{non-uuid} returns 400") {
        withSeasonApp { client ->
            val response = client.get("/api/v1/seasons/not-a-uuid")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/seasons creates the season and returns 201") {
        val newId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000333"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeSeasonRepository(idFactory = { newId }, clock = { newTime })

        withSeasonApp(fake = fake) { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateSeasonRequest(
                            leagueId = CASTLEWOOD_ID,
                            name = "PGA 2026",
                            seasonYear = 2026,
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Created
            val created: Season = response.body()
            created.id shouldBe newId
            created.status shouldBe "draft"
            created.seasonNumber shouldBe 1
            created.maxTeams shouldBe 10
        }
    }

    test("PUT /api/v1/seasons/{id} applies partial update and returns 200") {
        val s = season()
        withSeasonApp(seeded = listOf(s)) { client ->
            val response =
                client.put("/api/v1/seasons/${s.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateSeasonRequest(status = "active", maxTeams = 13))
                }

            response.status shouldBe HttpStatusCode.OK
            val updated: Season = response.body()
            updated.status shouldBe "active"
            updated.maxTeams shouldBe 13
            updated.name shouldBe s.name
        }
    }

    test("PUT /api/v1/seasons/{unknown-uuid} returns 404") {
        withSeasonApp { client ->
            val response =
                client.put("/api/v1/seasons/${UUID.randomUUID()}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateSeasonRequest(name = "Ghost"))
                }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/seasons/{id}/rules returns the default rules") {
        val s = season()
        withSeasonApp(seeded = listOf(s)) { client ->
            val response = client.get("/api/v1/seasons/${s.id.value}/rules")

            response.status shouldBe HttpStatusCode.OK
            val rules: SeasonRules = response.body()
            rules.payouts shouldBe SeasonRules.DEFAULT_PAYOUTS
            rules.sideBetRounds shouldBe SeasonRules.DEFAULT_SIDE_BET_ROUNDS
        }
    }

    test("GET /api/v1/seasons/{unknown}/rules returns 404") {
        withSeasonApp { client ->
            val response = client.get("/api/v1/seasons/${UUID.randomUUID()}/rules")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})

private suspend fun withSeasonApp(
    seeded: List<Season> = emptyList(),
    fake: FakeSeasonRepository = FakeSeasonRepository(initial = seeded),
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) {
    testApplication {
        application {
            module(
                healthProbe = ALWAYS_HEALTHY,
                leagueService = LeagueService(FakeLeagueRepository()),
                golferService = GolferService(FakeGolferRepository()),
                seasonService = SeasonService(fake),
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
