package com.cwfgw.http

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

private const val CLIENT_MARKER = "zq7-client-marker"

private fun queryParamTestApp(block: suspend (HttpClient) -> Unit) =
    testApplication {
        application {
            install(ServerContentNegotiation) { json(Json) }
            installErrorHandling()
            routing {
                get("/week") {
                    val week = optionalQueryParam("week", String::toIntOrNull)
                    call.respondText(week?.toString() ?: "none")
                }
            }
        }
        block(client)
    }

class QueryParamsSpec : FunSpec({

    test("an absent parameter is null") {
        queryParamTestApp { client ->
            client.get("/week").bodyAsText() shouldBe "none"
        }
    }

    test("a well-formed parameter is parsed") {
        queryParamTestApp { client ->
            client.get("/week?week=7").bodyAsText() shouldBe "7"
        }
    }

    test("a malformed parameter is a 400 that names the parameter and does not echo its value") {
        queryParamTestApp { client ->
            val response = client.get("/week?week=$CLIENT_MARKER")

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldBe """{"error":"invalid query parameter week"}"""
        }
    }
})
