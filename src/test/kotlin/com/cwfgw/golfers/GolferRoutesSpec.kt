package com.cwfgw.golfers

import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Instant
import java.util.UUID

private val RORY =
    Golfer(
        id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000101")),
        pgaPlayerId = "pga-rory",
        firstName = "Rory",
        lastName = "McIlroy",
        country = "NIR",
        worldRanking = 2,
        active = true,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private val TIGER =
    Golfer(
        id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000102")),
        pgaPlayerId = "pga-tiger",
        firstName = "Tiger",
        lastName = "Woods",
        country = "USA",
        worldRanking = null,
        active = false,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun golfers(vararg seeded: Golfer): ApiFixture.() -> Unit =
    {
        golferService = GolferService(FakeGolferRepository(initial = seeded.toList()), FakeTransactor())
    }

class GolferRoutesSpec : FunSpec({

    test("GET /api/v1/golfers defaults to active=true") {
        apiTest(golfers(RORY, TIGER)) { client ->
            val response = client.get("/api/v1/golfers")

            response.status shouldBe HttpStatusCode.OK
            val golfers: List<Golfer> = response.body()
            golfers.map { it.id } shouldContainExactly listOf(RORY.id)
        }
    }

    test("GET /api/v1/golfers?active=false returns every golfer") {
        apiTest(golfers(RORY, TIGER)) { client ->
            val response = client.get("/api/v1/golfers?active=false")

            response.status shouldBe HttpStatusCode.OK
            val golfers: List<Golfer> = response.body()
            golfers.map { it.id } shouldContainExactly listOf(RORY.id, TIGER.id)
        }
    }

    test("GET /api/v1/golfers?search=wood matches Woods case-insensitively") {
        apiTest(golfers(RORY, TIGER)) { client ->
            val response = client.get("/api/v1/golfers?active=false&search=wood")

            response.status shouldBe HttpStatusCode.OK
            val golfers: List<Golfer> = response.body()
            golfers.map { it.id } shouldContainExactly listOf(TIGER.id)
        }
    }

    test("GET /api/v1/golfers?active={non-bool} returns 400 instead of silently defaulting to true") {
        apiTest { client ->
            val response = client.get("/api/v1/golfers?active=maybe")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/golfers/{id} returns 200 with the golfer") {
        apiTest(golfers(RORY)) { client ->
            val response = client.get("/api/v1/golfers/${RORY.id.value}")

            response.status shouldBe HttpStatusCode.OK
            response.body<Golfer>() shouldBe RORY
        }
    }

    test("GET /api/v1/golfers/{unknown-uuid} returns 404") {
        apiTest { client ->
            val response = client.get("/api/v1/golfers/${UUID.randomUUID()}")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/golfers/{non-uuid} returns 400") {
        apiTest { client ->
            val response = client.get("/api/v1/golfers/not-a-uuid")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/golfers creates the golfer and returns 201") {
        val newId = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000222"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeGolferRepository(idFactory = { newId }, clock = { newTime })

        authenticatedApiTest({ golferService = GolferService(fake, FakeTransactor()) }) { client ->
            val response =
                client.post("/api/v1/golfers") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler", worldRanking = 1))
                }

            response.status shouldBe HttpStatusCode.Created
            val created: Golfer = response.body()
            created.id shouldBe newId
            created.firstName shouldBe "Scottie"
            created.active shouldBe true
            created.updatedAt shouldBe newTime
        }
    }

    test("PUT /api/v1/golfers/{id} applies partial updates and returns 200") {
        authenticatedApiTest(golfers(RORY)) { client ->
            val response =
                client.put("/api/v1/golfers/${RORY.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateGolferRequest(worldRanking = 1, active = false))
                }

            response.status shouldBe HttpStatusCode.OK
            val updated: Golfer = response.body()
            updated.worldRanking shouldBe 1
            updated.active shouldBe false
            updated.firstName shouldBe "Rory"
        }
    }

    test("PUT /api/v1/golfers/{unknown-uuid} returns 404") {
        authenticatedApiTest { client ->
            val response =
                client.put("/api/v1/golfers/${UUID.randomUUID()}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateGolferRequest(firstName = "Ghost"))
                }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/golfers returns 401 without an authenticated session") {
        apiTest { client ->
            val response =
                client.post("/api/v1/golfers") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateGolferRequest(firstName = "Anon", lastName = "Anon"))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("PUT /api/v1/golfers/{id} returns 401 without an authenticated session") {
        apiTest(golfers(RORY)) { client ->
            val response =
                client.put("/api/v1/golfers/${RORY.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateGolferRequest(active = false))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
