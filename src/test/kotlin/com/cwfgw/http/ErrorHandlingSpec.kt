package com.cwfgw.http

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.get as routingGet

private fun errorHandlingTestApp(block: suspend (HttpClient) -> Unit) =
    testApplication {
        application {
            install(ServerContentNegotiation) { json(Json) }
            installErrorHandling()
            routing {
                routingGet("/throw/not-found") { throw DomainError.NotFound("league xyz") }
                routingGet("/throw/validation") { throw DomainError.Validation("bad input") }
                routingGet("/throw/conflict") { throw DomainError.Conflict("already exists") }
                routingGet("/throw/unmapped") { error("boom") }
            }
        }
        val client =
            createClient {
                install(ContentNegotiation) { json(Json) }
            }
        block(client)
    }

class ErrorHandlingSpec : FunSpec({

    test("DomainError.NotFound maps to 404 with the error message in the body") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/not-found")

            response.status shouldBe HttpStatusCode.NotFound
            response.body<ErrorBody>().error shouldBe "league xyz"
        }
    }

    test("DomainError.Validation maps to 400 with the error message in the body") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/validation")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "bad input"
        }
    }

    test("DomainError.Conflict maps to 409 with the error message in the body") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/conflict")

            response.status shouldBe HttpStatusCode.Conflict
            response.body<ErrorBody>().error shouldBe "already exists"
        }
    }

    test("Unmapped exceptions map to 500 with a generic message that does not leak internal detail") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/unmapped")

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<ErrorBody>().error shouldBe "Internal server error"
        }
    }
})
