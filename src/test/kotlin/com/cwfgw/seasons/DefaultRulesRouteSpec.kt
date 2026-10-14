package com.cwfgw.seasons

import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.regularUserApiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class DefaultRulesRouteSpec : FunSpec({

    test("GET /api/v1/seasons/default-rules serves SeasonRules.defaults() to an admin") {
        authenticatedApiTest { client ->
            val response = client.get("/api/v1/seasons/default-rules")

            response.status shouldBe HttpStatusCode.OK
            response.body<SeasonRules>() shouldBe SeasonRules.defaults()
        }
    }

    test("GET /api/v1/seasons/default-rules returns 401 without an authenticated session") {
        apiTest { client ->
            client.get("/api/v1/seasons/default-rules").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/seasons/default-rules rejects a regular user with 403") {
        regularUserApiTest { client ->
            client.get("/api/v1/seasons/default-rules").status shouldBe HttpStatusCode.Forbidden
        }
    }
})
