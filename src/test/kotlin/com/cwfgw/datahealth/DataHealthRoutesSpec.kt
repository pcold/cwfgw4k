package com.cwfgw.datahealth

import com.cwfgw.golfers.GolferId
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.regularUserApiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import java.util.UUID

private val ROSTERED =
    DataHealthGolfer(
        id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000201")),
        firstName = "Si Woo",
        lastName = "Kim",
        pgaPlayerId = null,
    )

private val TWIN =
    ROSTERED.copy(id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000202")), pgaPlayerId = "7081")

private val BLANK =
    ROSTERED.copy(id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000203")), firstName = " ")

class DataHealthRoutesSpec : FunSpec({

    test("GET /api/v1/admin/data-health refuses a caller with no session") {
        apiTest { client ->
            client.get("/api/v1/admin/data-health").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/admin/data-health refuses a regular user with 403") {
        regularUserApiTest { client ->
            client.get("/api/v1/admin/data-health").status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("GET /api/v1/admin/data-health gives an admin each check's count beside its rows") {
        val repository =
            FakeDataHealthRepository(
                rosteredTwins = listOf(RosteredTwinGroup("si woo kim", listOf(ROSTERED), listOf(TWIN))),
                rosteredWithoutEspnId = listOf(ROSTERED),
                blankFirstNames = listOf(BLANK),
            )
        authenticatedApiTest({ dataHealthService = DataHealthService(repository, transactor) }) { client ->
            val response = client.get("/api/v1/admin/data-health")

            response.status shouldBe HttpStatusCode.OK
            Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement(EXPECTED_REPORT)
        }
    }
})

private const val EXPECTED_REPORT = """
{
  "rostered_twins": {
    "rows": [
      {
        "normalized_name": "si woo kim",
        "rostered_without_espn_id": [
          {
            "id": "00000000-0000-0000-0000-000000000201",
            "first_name": "Si Woo",
            "last_name": "Kim",
            "pga_player_id": null
          }
        ],
        "holding_espn_id": [
          {
            "id": "00000000-0000-0000-0000-000000000202",
            "first_name": "Si Woo",
            "last_name": "Kim",
            "pga_player_id": "7081"
          }
        ]
      }
    ],
    "count": 1
  },
  "rostered_without_espn_id": {
    "rows": [
      {"id": "00000000-0000-0000-0000-000000000201", "first_name": "Si Woo", "last_name": "Kim", "pga_player_id": null}
    ],
    "count": 1
  },
  "blank_first_names": {
    "rows": [
      {"id": "00000000-0000-0000-0000-000000000203", "first_name": " ", "last_name": "Kim", "pga_player_id": null}
    ],
    "count": 1
  }
}
"""
