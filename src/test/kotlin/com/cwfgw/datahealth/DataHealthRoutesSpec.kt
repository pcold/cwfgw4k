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
        authenticatedApiTest({ dataHealthRepository = repository }) { client ->
            val response = client.get("/api/v1/admin/data-health")

            response.status shouldBe HttpStatusCode.OK
            Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement(EXPECTED_REPORT)
        }
    }
})

private const val EXPECTED_REPORT = """
{
  "rosteredTwins": {
    "rows": [
      {
        "normalizedName": "si woo kim",
        "rosteredWithoutEspnId": [
          {
            "id": "00000000-0000-0000-0000-000000000201",
            "firstName": "Si Woo",
            "lastName": "Kim",
            "pgaPlayerId": null
          }
        ],
        "holdingEspnId": [
          {
            "id": "00000000-0000-0000-0000-000000000202",
            "firstName": "Si Woo",
            "lastName": "Kim",
            "pgaPlayerId": "7081"
          }
        ]
      }
    ],
    "count": 1
  },
  "rosteredWithoutEspnId": {
    "rows": [
      {"id": "00000000-0000-0000-0000-000000000201", "firstName": "Si Woo", "lastName": "Kim", "pgaPlayerId": null}
    ],
    "count": 1
  },
  "blankFirstNames": {
    "rows": [
      {"id": "00000000-0000-0000-0000-000000000203", "firstName": " ", "lastName": "Kim", "pgaPlayerId": null}
    ],
    "count": 1
  }
}
"""
