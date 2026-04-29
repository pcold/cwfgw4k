package com.cwfgw.users

import com.cwfgw.testing.regularUserApiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID

/**
 * One canonical "regular user gets 403" check per route file with data-changing
 * endpoints. The full success / 401 / domain-error matrix lives in each route
 * file's own spec; this spec exists so a regression that drops [requireAdmin]
 * from a route file fails one obvious test instead of silently passing.
 *
 * Each test hits one representative POST/DELETE per route file. Bodies are
 * minimal because the gate runs before [io.ktor.server.request.receive], so
 * a 403 response is independent of payload shape.
 */
class AdminGateSpec : FunSpec({

    val anySeasonId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
    val anyTournamentId = UUID.fromString("00000000-0000-0000-0000-0000000000bb")
    val anyId = UUID.fromString("00000000-0000-0000-0000-0000000000cc")

    test("LeagueRoutes: POST /api/v1/leagues rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response =
                client.post("/api/v1/leagues") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("GolferRoutes: POST /api/v1/golfers rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response =
                client.post("/api/v1/golfers") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("SeasonRoutes: DELETE /api/v1/seasons/{id} rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response = client.delete("/api/v1/seasons/$anyId")
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("SeasonOpsRoutes: POST /api/v1/seasons/{sid}/finalize rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response = client.post("/api/v1/seasons/$anySeasonId/finalize")
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("TeamRoutes: POST /api/v1/seasons/{sid}/teams rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response =
                client.post("/api/v1/seasons/$anySeasonId/teams") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("DraftRoutes: POST /api/v1/seasons/{sid}/draft/start rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response = client.post("/api/v1/seasons/$anySeasonId/draft/start")
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("ScoringRoutes: POST /api/v1/seasons/{sid}/scoring/refresh-standings rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response = client.post("/api/v1/seasons/$anySeasonId/scoring/refresh-standings")
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("TournamentRoutes: POST /api/v1/tournaments rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response =
                client.post("/api/v1/tournaments") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("TournamentOpsRoutes: POST /api/v1/tournaments/{id}/finalize rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response = client.post("/api/v1/tournaments/$anyTournamentId/finalize")
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("EspnRoutes: POST /api/v1/espn/import rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response = client.post("/api/v1/espn/import?date=2026-01-01")
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("AdminRoutes: POST /api/v1/admin/roster/preview rejects a regular user with 403") {
        regularUserApiTest { client ->
            val response =
                client.post("/api/v1/admin/roster/preview") {
                    contentType(ContentType.Text.Plain)
                    setBody("anything")
                }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }
})
