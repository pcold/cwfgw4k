package com.cwfgw.leagues

import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Instant
import java.util.UUID

private val CASTLEWOOD_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
private val CASTLEWOOD_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z")
private val CASTLEWOOD_SEED =
    League(id = CASTLEWOOD_ID, name = "Castlewood Fantasy Golf", createdAt = CASTLEWOOD_CREATED_AT)

class LeagueRoutesSpec : FunSpec({

    test("GET /api/v1/leagues returns the seeded leagues") {
        apiTest({ leagueService = LeagueService(FakeLeagueRepository(initial = listOf(CASTLEWOOD_SEED))) }) { client ->
            val response = client.get("/api/v1/leagues")

            response.status shouldBe HttpStatusCode.OK
            val leagues: List<League> = response.body()
            leagues.map { it.name } shouldContain "Castlewood Fantasy Golf"
        }
    }

    test("GET /api/v1/leagues/{id} returns the league with 200") {
        apiTest({ leagueService = LeagueService(FakeLeagueRepository(initial = listOf(CASTLEWOOD_SEED))) }) { client ->
            val response = client.get("/api/v1/leagues/${CASTLEWOOD_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            val league: League = response.body()
            league.id shouldBe CASTLEWOOD_ID
            league.name shouldBe "Castlewood Fantasy Golf"
        }
    }

    test("GET /api/v1/leagues/{unknown-uuid} returns 404") {
        apiTest({ leagueService = LeagueService(FakeLeagueRepository(initial = listOf(CASTLEWOOD_SEED))) }) { client ->
            val response = client.get("/api/v1/leagues/${UUID.randomUUID()}")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/leagues/{non-uuid} returns 400") {
        apiTest { client ->
            val response = client.get("/api/v1/leagues/not-a-uuid")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/leagues creates the league and exposes it via GET") {
        val newId = LeagueId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val newCreatedAt = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeLeagueRepository(idFactory = { newId }, clock = { newCreatedAt })

        apiTest({ leagueService = LeagueService(fake) }) { client ->
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
