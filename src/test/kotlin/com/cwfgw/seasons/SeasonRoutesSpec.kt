package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.seedSeasons
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

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
        tieFloor = BigDecimal.ONE,
        sideBetAmount = BigDecimal(15),
        maxTeams = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class SeasonRoutesSpec : FunSpec({

    test("GET /api/v1/seasons returns seeded seasons") {
        val s = season()
        apiTest({ seedSeasons(s) }) { client ->
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

        apiTest({ seedSeasons(castlewoodSeason, otherSeason) }) { client ->
            val response = client.get("/api/v1/seasons?league_id=${CASTLEWOOD_ID.value}")

            response.status shouldBe HttpStatusCode.OK
            val seasons: List<Season> = response.body()
            seasons.map { it.id } shouldContainExactly listOf(castlewoodSeason.id)
        }
    }

    test("GET /api/v1/seasons?year filters by season_year") {
        val y2025 = season(name = "2025", seasonYear = 2025)
        val y2026 = season(name = "2026", seasonYear = 2026)

        apiTest({ seedSeasons(y2025, y2026) }) { client ->
            val response = client.get("/api/v1/seasons?year=2025")

            val seasons: List<Season> = response.body()
            seasons.map { it.id } shouldContainExactly listOf(y2025.id)
        }
    }

    test("GET /api/v1/seasons?league_id={non-uuid} returns 400 instead of silently ignoring the filter") {
        apiTest { client ->
            val response = client.get("/api/v1/seasons?league_id=not-a-uuid")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/seasons?year={non-int} returns 400 instead of silently ignoring the filter") {
        apiTest { client ->
            val response = client.get("/api/v1/seasons?year=spring")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/seasons creates the season and returns 201") {
        val newId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000333"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeSeasonRepository(idFactory = { newId }, clock = { newTime })

        authenticatedApiTest({ seasonRepository = fake }) { client ->
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
            created.seasonNumber shouldBe 1
            created.maxTeams shouldBe 10
        }
    }

    test("POST /api/v1/seasons drops a flat tie_floor and side_bet_amount, creating the season at the defaults") {
        val flatOnly =
            """{"league_id":"${CASTLEWOOD_ID.value}","name":"Flat","season_year":2026,""" +
                """"tie_floor":"0.5","side_bet_amount":"-15"}"""

        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(flatOnly)
                }

            response.status shouldBe HttpStatusCode.Created
            val created: Season = response.body()
            created.tieFloor.compareTo(SeasonRules.DEFAULT_TIE_FLOOR) shouldBe 0
            created.sideBetAmount.compareTo(SeasonRules.DEFAULT_SIDE_BET_AMOUNT) shouldBe 0
        }
    }

    test("POST /api/v1/seasons returns 401 without an authenticated session") {
        apiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateSeasonRequest(leagueId = CASTLEWOOD_ID, name = "anon", seasonYear = 2026))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("DELETE /api/v1/seasons/{id} returns 204 and the season leaves the list") {
        val s = season()
        authenticatedApiTest({ seedSeasons(s) }) { client ->
            val deleteResponse = client.delete("/api/v1/seasons/${s.id.value}")
            deleteResponse.status shouldBe HttpStatusCode.NoContent

            val listResponse = client.get("/api/v1/seasons")
            listResponse.body<List<Season>>().shouldBeEmpty()
        }
    }

    test("DELETE /api/v1/seasons/{unknown-uuid} returns 404") {
        authenticatedApiTest { client ->
            val response = client.delete("/api/v1/seasons/${UUID.randomUUID()}")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("DELETE /api/v1/seasons/{id} returns 401 without an authenticated session") {
        val s = season()
        apiTest({ seedSeasons(s) }) { client ->
            val response = client.delete("/api/v1/seasons/${s.id.value}")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/seasons/{id}/rules returns the default rules") {
        val s = season()
        apiTest({ seedSeasons(s) }) { client ->
            val response = client.get("/api/v1/seasons/${s.id.value}/rules")

            response.status shouldBe HttpStatusCode.OK
            val rules: SeasonRules = response.body()
            rules.payouts shouldBe SeasonRules.DEFAULT_PAYOUTS
            rules.sideBetRounds shouldBe SeasonRules.DEFAULT_SIDE_BET_ROUNDS
        }
    }

    test("GET /api/v1/seasons/{unknown}/rules returns 404") {
        apiTest { client ->
            val response = client.get("/api/v1/seasons/${UUID.randomUUID()}/rules")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
