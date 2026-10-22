package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.seedTournaments
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))
private val OTHER_SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa2"))

private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    name: String = "The Masters",
    seasonId: SeasonId = SEASON_ID,
    startDate: LocalDate = LocalDate.parse("2026-04-09"),
    status: TournamentStatus = TournamentStatus.Upcoming,
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
        isTeamEvent = false,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

private suspend fun HttpResponse.wireJson(): JsonElement = Json.parseToJsonElement(bodyAsText())

class TournamentRoutesSpec : FunSpec({

    test("GET /api/v1/tournaments returns every tournament") {
        val masters = tournament(name = "Masters")
        val open = tournament(name = "Open", startDate = LocalDate.parse("2026-07-16"))

        apiTest({ seedTournaments(masters, open) }) { client ->
            val response = client.get("/api/v1/tournaments")

            response.status shouldBe HttpStatusCode.OK
            val body: List<Tournament> = response.body()
            body.map { it.id } shouldContainExactly listOf(masters.id, open.id)
        }
    }

    test("GET /api/v1/tournaments reports a started, unfinalized week as in_progress") {
        // Dates clear of today, so a run that crosses midnight Eastern can't move either one.
        val today = LocalDate.now(ESPN_SCHEDULE_ZONE)
        val started = tournament(name = "Started", startDate = today.minusDays(1))
        val later = tournament(name = "Later", startDate = today.plusDays(2))
        val finalized =
            tournament(name = "Finalized", startDate = today.minusDays(7), status = TournamentStatus.Completed)

        apiTest({ seedTournaments(started, later, finalized) }) { client ->
            val response = client.get("/api/v1/tournaments")

            response.status shouldBe HttpStatusCode.OK
            val wire =
                response.wireJson().jsonArray.associate { row ->
                    row.jsonObject.getValue("name") to row.jsonObject.getValue("status")
                }
            wire shouldBe
                mapOf(
                    JsonPrimitive("Started") to JsonPrimitive("in_progress"),
                    JsonPrimitive("Later") to JsonPrimitive("upcoming"),
                    JsonPrimitive("Finalized") to JsonPrimitive("completed"),
                )
        }
    }

    test("GET /api/v1/tournaments?season_id= returns only that season's tournaments") {
        val inSeason = tournament(name = "In season")
        val otherSeason = tournament(name = "Other season", seasonId = OTHER_SEASON_ID)

        apiTest({ seedTournaments(inSeason, otherSeason) }) { client ->
            val response = client.get("/api/v1/tournaments?season_id=${SEASON_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            val body: List<Tournament> = response.body()
            body.map { it.id } shouldContainExactly listOf(inSeason.id)
        }
    }

    test("GET /api/v1/tournaments?season_id={non-uuid} returns 400") {
        apiTest { client ->
            client.get("/api/v1/tournaments?season_id=not-a-uuid").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/tournaments/current names the earliest unfinalized tournament") {
        val players = tournament(name = "Players", status = TournamentStatus.Completed)
        val masters = tournament(name = "Masters", startDate = LocalDate.parse("2026-04-16"))

        apiTest({ seedTournaments(players, masters) }) { client ->
            val response = client.get("/api/v1/tournaments/current?season_id=${SEASON_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            response.wireJson() shouldBe
                Json.parseToJsonElement("""{"type": "unfinalized", "tournamentId": "${masters.id.value}"}""")
        }
    }

    test("GET /api/v1/tournaments/current names the latest tournament of a finalized season") {
        val players = tournament(name = "Players", status = TournamentStatus.Completed)
        val masters =
            tournament(name = "Masters", startDate = LocalDate.parse("2026-04-16"), status = TournamentStatus.Completed)

        apiTest({ seedTournaments(players, masters) }) { client ->
            val response = client.get("/api/v1/tournaments/current?season_id=${SEASON_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            response.wireJson() shouldBe
                Json.parseToJsonElement("""{"type": "season_finalized", "tournamentId": "${masters.id.value}"}""")
        }
    }

    test("GET /api/v1/tournaments/current answers no_tournaments for a season with none") {
        apiTest { client ->
            val response = client.get("/api/v1/tournaments/current?season_id=${SEASON_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            response.wireJson() shouldBe Json.parseToJsonElement("""{"type": "no_tournaments"}""")
        }
    }

    test("GET /api/v1/tournaments/current without a season_id returns 400") {
        apiTest { client ->
            client.get("/api/v1/tournaments/current").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/tournaments/current?season_id={non-uuid} returns 400") {
        apiTest { client ->
            client.get("/api/v1/tournaments/current?season_id=not-a-uuid").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("PUT /api/v1/tournaments/{id} applies partial updates") {
        val masters = tournament(name = "Masters")

        authenticatedApiTest({ seedTournaments(masters) }) { client ->
            val response =
                client.put("/api/v1/tournaments/${masters.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTournamentRequest(status = TournamentStatus.InProgress))
                }

            response.status shouldBe HttpStatusCode.OK
            response.body<Tournament>().status shouldBe TournamentStatus.InProgress
        }
    }

    // A raw body, not a serialized request: a client sharing the server's Json would agree with it on any naming.
    test("PUT /api/v1/tournaments/{id} reads and writes each key as its Kotlin property names it") {
        val masters = tournament(name = "Masters")

        authenticatedApiTest({ seedTournaments(masters) }) { client ->
            val response =
                client.put("/api/v1/tournaments/${masters.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"purseAmount": 20000000, "status": "in_progress"}""")
                }

            response.status shouldBe HttpStatusCode.OK
            val wire = response.wireJson().jsonObject
            wire["purseAmount"] shouldBe JsonPrimitive(20_000_000)
            wire["status"] shouldBe JsonPrimitive("in_progress")
        }
    }

    test("PUT /api/v1/tournaments/{unknown} returns 404") {
        authenticatedApiTest { client ->
            val response =
                client.put("/api/v1/tournaments/${UUID.randomUUID()}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTournamentRequest(status = TournamentStatus.InProgress))
                }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("PUT /api/v1/tournaments/{non-uuid} returns 400") {
        authenticatedApiTest { client ->
            val response =
                client.put("/api/v1/tournaments/not-a-uuid") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTournamentRequest(status = TournamentStatus.InProgress))
                }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("PUT /api/v1/tournaments/{id} returns 401 without an authenticated session") {
        val masters = tournament(name = "Masters")
        apiTest({ seedTournaments(masters) }) { client ->
            val response =
                client.put("/api/v1/tournaments/${masters.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTournamentRequest(status = TournamentStatus.Completed))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
