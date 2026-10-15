package com.cwfgw.golfers

import com.cwfgw.testing.apiTest
import com.cwfgw.testing.seedGolfers
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
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
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class GolferRoutesSpec : FunSpec({

    test("GET /api/v1/golfers returns every golfer") {
        apiTest({ seedGolfers(RORY, TIGER) }) { client ->
            val response = client.get("/api/v1/golfers")

            response.status shouldBe HttpStatusCode.OK
            val golfers: List<Golfer> = response.body()
            golfers.map { it.id } shouldContainExactly listOf(RORY.id, TIGER.id)
        }
    }

    test("GET /api/v1/golfers?search=wood matches Woods case-insensitively") {
        apiTest({ seedGolfers(RORY, TIGER) }) { client ->
            val response = client.get("/api/v1/golfers?search=wood")

            response.status shouldBe HttpStatusCode.OK
            val golfers: List<Golfer> = response.body()
            golfers.map { it.id } shouldContainExactly listOf(TIGER.id)
        }
    }

    test("GET /api/v1/golfers leaves out a tombstone, which no admin may link") {
        apiTest({ seedGolfers(RORY, TIGER.copy(tombstonedAt = Instant.parse("2026-10-15T00:00:00Z"))) }) { client ->
            val golfers: List<Golfer> = client.get("/api/v1/golfers").body()

            golfers.map { it.id } shouldContainExactly listOf(RORY.id)
        }
    }
})
