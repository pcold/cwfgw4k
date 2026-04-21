package com.cwfgw.health

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

private val ALWAYS_CONNECTED = HealthProbe { true }
private val NEVER_CONNECTED = HealthProbe { false }

class HealthRoutesSpec : FunSpec({
    test("GET /api/v1/health returns ok when the probe reports connected") {
        testApplication {
            application {
                module(
                    ALWAYS_CONNECTED,
                    LeagueService(FakeLeagueRepository()),
                    GolferService(FakeGolferRepository()),
                )
            }

            val response = client.get("/api/v1/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"ok","service":"cwfgw","database":"connected"}"""
        }
    }

    test("GET /api/v1/health returns degraded when the probe reports unreachable") {
        testApplication {
            application {
                module(
                    NEVER_CONNECTED,
                    LeagueService(FakeLeagueRepository()),
                    GolferService(FakeGolferRepository()),
                )
            }

            val response = client.get("/api/v1/health")

            response.status shouldBe HttpStatusCode.InternalServerError
            response.bodyAsText() shouldBe """{"status":"degraded","service":"cwfgw","database":"unreachable"}"""
        }
    }
})
