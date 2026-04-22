package com.cwfgw.health

import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

class HealthRoutesSpec : FunSpec({
    test("GET /api/v1/health returns ok when the probe reports connected") {
        apiTest({ healthProbe = HealthProbe { true } }) { client ->
            val response = client.get("/api/v1/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"ok","service":"cwfgw","database":"connected"}"""
        }
    }

    test("GET /api/v1/health returns degraded when the probe reports unreachable") {
        apiTest({ healthProbe = HealthProbe { false } }) { client ->
            val response = client.get("/api/v1/health")

            response.status shouldBe HttpStatusCode.InternalServerError
            response.bodyAsText() shouldBe """{"status":"degraded","service":"cwfgw","database":"unreachable"}"""
        }
    }
})
