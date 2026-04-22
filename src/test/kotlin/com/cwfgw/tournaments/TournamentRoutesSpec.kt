package com.cwfgw.tournaments

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    name: String = "The Masters",
    seasonId: SeasonId = SEASON_ID,
    startDate: LocalDate = LocalDate.parse("2026-04-09"),
    status: String = "upcoming",
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = seasonId,
        startDate = startDate,
        endDate = startDate.plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

private fun tournaments(vararg seeded: Tournament): ApiFixture.() -> Unit =
    {
        tournamentService = TournamentService(FakeTournamentRepository(initial = seeded.toList()))
    }

class TournamentRoutesSpec : FunSpec({

    test("GET /api/v1/tournaments returns every tournament") {
        val masters = tournament(name = "Masters")
        val open = tournament(name = "Open", startDate = LocalDate.parse("2026-07-16"))

        apiTest(tournaments(masters, open)) { client ->
            val response = client.get("/api/v1/tournaments")

            response.status shouldBe HttpStatusCode.OK
            val body: List<Tournament> = response.body()
            body.map { it.id } shouldContainExactly listOf(masters.id, open.id)
        }
    }

    test("GET /api/v1/tournaments?season_id=&status= filters both") {
        val upcoming = tournament(name = "Upcoming", status = "upcoming")
        val completed = tournament(name = "Completed", status = "completed")

        apiTest(tournaments(upcoming, completed)) { client ->
            val response =
                client.get("/api/v1/tournaments?season_id=${SEASON_ID.value}&status=completed")

            response.status shouldBe HttpStatusCode.OK
            val body: List<Tournament> = response.body()
            body.map { it.id } shouldContainExactly listOf(completed.id)
        }
    }

    test("GET /api/v1/tournaments?season_id={non-uuid} returns 400") {
        apiTest { client ->
            client.get("/api/v1/tournaments?season_id=not-a-uuid").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/tournaments/{id} returns 200 with the tournament") {
        val masters = tournament(name = "Masters")

        apiTest(tournaments(masters)) { client ->
            val response = client.get("/api/v1/tournaments/${masters.id.value}")

            response.status shouldBe HttpStatusCode.OK
            response.body<Tournament>() shouldBe masters
        }
    }

    test("GET /api/v1/tournaments/{unknown} returns 404") {
        apiTest { client ->
            client.get("/api/v1/tournaments/${UUID.randomUUID()}").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/tournaments/{non-uuid} returns 400") {
        apiTest { client ->
            client.get("/api/v1/tournaments/not-a-uuid").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/tournaments creates a tournament returning 201") {
        val newId = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000cc1"))
        val newTime = Instant.parse("2026-04-01T00:00:00Z")
        val fake = FakeTournamentRepository(idFactory = { newId }, clock = { newTime })

        apiTest({ tournamentService = TournamentService(fake) }) { client ->
            val response =
                client.post("/api/v1/tournaments") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateTournamentRequest(
                            name = "The Masters",
                            seasonId = SEASON_ID,
                            startDate = LocalDate.parse("2026-04-09"),
                            endDate = LocalDate.parse("2026-04-12"),
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Created
            val created: Tournament = response.body()
            created.id shouldBe newId
            created.status shouldBe "upcoming"
        }
    }

    test("PUT /api/v1/tournaments/{id} applies partial updates") {
        val masters = tournament(name = "Masters")

        apiTest(tournaments(masters)) { client ->
            val response =
                client.put("/api/v1/tournaments/${masters.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTournamentRequest(status = "in_progress"))
                }

            response.status shouldBe HttpStatusCode.OK
            response.body<Tournament>().status shouldBe "in_progress"
        }
    }

    test("GET /api/v1/tournaments/{id}/results returns an empty list when no results have been imported") {
        val masters = tournament(name = "Masters")

        apiTest(tournaments(masters)) { client ->
            val response = client.get("/api/v1/tournaments/${masters.id.value}/results")

            response.status shouldBe HttpStatusCode.OK
            response.body<List<TournamentResult>>() shouldBe emptyList()
        }
    }

    test("POST /api/v1/tournaments/{id}/results imports a batch and returns the persisted results") {
        val masters = tournament(name = "Masters")
        val rory = GolferId(UUID.randomUUID())
        val scottie = GolferId(UUID.randomUUID())

        apiTest(tournaments(masters)) { client ->
            val response =
                client.post("/api/v1/tournaments/${masters.id.value}/results") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        listOf(
                            CreateTournamentResultRequest(golferId = rory, position = 2),
                            CreateTournamentResultRequest(golferId = scottie, position = 1),
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.OK
            val body: List<TournamentResult> = response.body()
            body.map { it.golferId.value } shouldContainExactly listOf(rory.value, scottie.value)
        }
    }
})
