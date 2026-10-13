package com.cwfgw.http

import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.get as routingGet
import io.ktor.server.routing.post as routingPost

private const val MALFORMED_BODY = "malformed or invalid request body"
private const val UNSUPPORTED_CONTENT_TYPE = "unsupported or missing content type"
private const val LOGIN_PATH = "/api/v1/auth/login"

@Serializable
private data class NameHolder(val name: String)

/**
 * `setBody(String)` labels itself `text/plain`, so a request carrying no `Content-Type` at all is
 * only reachable through content that declares no type of its own.
 */
private class UnlabeledBody(private val text: String) : OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = text.toByteArray()
}

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
                routingGet("/throw/payload-too-large") { throw PayloadTooLargeException(sizeLimit = 1_024) }
                routingPost("/receive/plain") {
                    call.receive<NameHolder>()
                    call.respond(HttpStatusCode.OK)
                }
                // The only receive here that raises UnsupportedMediaTypeException rather than
                // CannotTransformContentToTypeException.
                routingPost("/receive/multipart") {
                    call.receive<MultiPartData>()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
        val client =
            createClient {
                install(ContentNegotiation) { json(Json) }
            }
        block(client)
    }

private suspend fun HttpClient.postJson(
    path: String,
    body: String,
) = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
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

    test("a truncated JSON body is a 400 with a fixed message") {
        errorHandlingTestApp { client ->
            val response = client.postJson("/receive/plain", """{"name":""")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe MALFORMED_BODY
        }
    }

    test("a mistyped field is a 400 whose body carries the fixed message and nothing from the parser") {
        errorHandlingTestApp { client ->
            val response = client.postJson("/receive/plain", """{"name":5}""")

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldBe """{"error":"malformed or invalid request body"}"""
        }
    }

    test("a content type no converter matches is a 415 with a fixed message") {
        errorHandlingTestApp { client ->
            val response =
                client.post("/receive/plain") {
                    contentType(ContentType.Text.Plain)
                    setBody("""{"name":"ada"}""")
                }

            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ErrorBody>().error shouldBe UNSUPPORTED_CONTENT_TYPE
        }
    }

    test("an absent Content-Type header is the same 415, because it matches no converter either") {
        errorHandlingTestApp { client ->
            val response = client.post("/receive/plain") { setBody(UnlabeledBody("""{"name":"ada"}""")) }

            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ErrorBody>().error shouldBe UNSUPPORTED_CONTENT_TYPE
        }
    }

    test("a multipart receive with no Content-Type is the same 415, though it fails with a different exception") {
        errorHandlingTestApp { client ->
            val response = client.post("/receive/multipart") { setBody(UnlabeledBody("x")) }

            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ErrorBody>().error shouldBe UNSUPPORTED_CONTENT_TYPE
        }
    }

    test("a payload over the size limit is not answered 415, though it shares a base type with both that are") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/payload-too-large")

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<ErrorBody>().error shouldBe "Internal server error"
        }
    }

    test("POST /api/v1/auth/login with a truncated JSON body is a 400, not a 500") {
        apiTest { client ->
            val response = client.postJson(LOGIN_PATH, """{"username":""")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe MALFORMED_BODY
        }
    }

    test("POST /api/v1/auth/login with a text/plain body is a 415, not a 500") {
        apiTest { client ->
            val response =
                client.post(LOGIN_PATH) {
                    contentType(ContentType.Text.Plain)
                    setBody("""{"username":"ada","password":"lovelace"}""")
                }

            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ErrorBody>().error shouldBe UNSUPPORTED_CONTENT_TYPE
        }
    }

    test("POST /api/v1/auth/login with no Content-Type is a 415, not a 500") {
        apiTest { client ->
            val response =
                client.post(LOGIN_PATH) { setBody(UnlabeledBody("""{"username":"ada","password":"lovelace"}""")) }

            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ErrorBody>().error shouldBe UNSUPPORTED_CONTENT_TYPE
        }
    }
})
