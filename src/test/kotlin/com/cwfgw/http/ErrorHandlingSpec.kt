package com.cwfgw.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import com.cwfgw.testing.CapturedLogs
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.captureLogs
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
private const val CLIENT_MARKER = "zq7-client-marker"

@Serializable
private data class NameHolder(val name: String)

private class NoSerializer

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
                routingPost("/receive/no-serializer") {
                    call.receive<NoSerializer>()
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

private class ClientMistake(
    val description: String,
    val answer: HttpStatusCode,
    val send: suspend (HttpClient) -> HttpResponse,
)

/**
 * What the handler can read back: the parser's exception quotes a rejected body, and a content type arrives on
 * the request and in the exception that refuses it.
 */
private val markedMistakes =
    listOf(
        ClientMistake("a truncated JSON body", HttpStatusCode.BadRequest) { client ->
            client.postJson("/receive/plain", """{"name":"$CLIENT_MARKER""")
        },
        ClientMistake("a mistyped field", HttpStatusCode.BadRequest) { client ->
            client.postJson("/receive/plain", """{"name":{"$CLIENT_MARKER":1}}""")
        },
        ClientMistake("an empty JSON body", HttpStatusCode.BadRequest) { client ->
            client.post("/receive/plain") {
                contentType(ContentType.Application.Json.withParameter("profile", CLIENT_MARKER))
                setBody("")
            }
        },
        ClientMistake("a content type no converter matches", HttpStatusCode.UnsupportedMediaType) { client ->
            client.post("/receive/plain") {
                contentType(ContentType("text", CLIENT_MARKER))
                setBody("""{"name":"ada"}""")
            }
        },
        ClientMistake("a multipart receive of another content type", HttpStatusCode.UnsupportedMediaType) { client ->
            client.post("/receive/multipart") {
                contentType(ContentType("text", CLIENT_MARKER))
                setBody("x")
            }
        },
    )

/**
 * Nothing here for a marker to ride on: there is no content type, and a body no converter took cannot be
 * received a second time.
 */
private val unmarkedMistakes =
    listOf(
        ClientMistake("an absent Content-Type header", HttpStatusCode.UnsupportedMediaType) { client ->
            client.post("/receive/plain") { setBody(UnlabeledBody("""{"name":"ada"}""")) }
        },
        ClientMistake("a multipart receive with no Content-Type", HttpStatusCode.UnsupportedMediaType) { client ->
            client.post("/receive/multipart") { setBody(UnlabeledBody("x")) }
        },
    )

/**
 * WARN counts with ERROR: `logback.xml` sends both to stderr, which Cloud Logging reads as severity ERROR.
 * Unfiltered by logger, so each line names its own and one from an unrelated thread says so in the failure.
 */
private fun CapturedLogs.atWarnOrAbove(): List<String> =
    events()
        .filter { it.level.isGreaterOrEqual(Level.WARN) }
        .map { "${it.level} ${it.loggerName} - ${it.formattedMessage}" }

/** With the stack trace: a cause attached to a line prints its message, and each nested cause's, beneath it. */
private fun CapturedLogs.rendered(): List<String> =
    events().map { it.formattedMessage + ThrowableProxyUtil.asString(it.throwableProxy) }

private fun CapturedLogs.unhandledErrorsOn(request: String): List<ILoggingEvent> =
    events().filter { it.formattedMessage == "Unhandled error on $request" }

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

    test("an empty JSON body is the same 400, though Ktor reports it as a content-type mismatch") {
        errorHandlingTestApp { client ->
            val response = client.postJson("/receive/plain", "")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe MALFORMED_BODY
        }
    }

    test("a receive into a type with no serializer is a 500, because the fault is the server's and not the body's") {
        errorHandlingTestApp { client ->
            val response = client.postJson("/receive/no-serializer", """{"name":"ada"}""")

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<ErrorBody>().error shouldBe "Internal server error"
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

    test("a payload over the size limit is not answered 415, though it shares a base type with the failures that are") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/payload-too-large")

            response.status shouldNotBe HttpStatusCode.UnsupportedMediaType
        }
    }

    test("POST /api/v1/auth/login with a truncated JSON body is a 400, not a 500") {
        apiTest { client ->
            val response = client.postJson(LOGIN_PATH, """{"username":""")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe MALFORMED_BODY
        }
    }

    test("POST /api/v1/auth/login with an empty JSON body is a 400, not a 415") {
        apiTest { client ->
            val response = client.postJson(LOGIN_PATH, "")

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe MALFORMED_BODY
        }
    }

    test("POST /api/v1/auth/login with a text/plain body is a 415 with the fixed message") {
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

    test("POST /api/v1/auth/login with no Content-Type is a 415 with the fixed message") {
        apiTest { client ->
            val response =
                client.post(LOGIN_PATH) { setBody(UnlabeledBody("""{"username":"ada","password":"lovelace"}""")) }

            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ErrorBody>().error shouldBe UNSUPPORTED_CONTENT_TYPE
        }
    }

    (markedMistakes + unmarkedMistakes).forEach { mistake ->
        test("${mistake.description} logs nothing at WARN or above") {
            captureLogs { logs ->
                errorHandlingTestApp { client -> mistake.send(client).status shouldBe mistake.answer }

                // Read after the application has shut down, not on the response: nothing says a handler
                // logs before it responds.
                logs.atWarnOrAbove().shouldBeEmpty()
            }
        }
    }

    markedMistakes.forEach { mistake ->
        test("${mistake.description} is echoed into neither the response nor a log line at any level") {
            captureLogs { logs ->
                errorHandlingTestApp { client ->
                    val response = mistake.send(client)

                    response.status shouldBe mistake.answer
                    response.bodyAsText() shouldNotContain CLIENT_MARKER
                }

                logs.rendered().filter { CLIENT_MARKER in it }.shouldBeEmpty()
            }
        }
    }

    test("DomainError.NotFound, Validation and Conflict log nothing at WARN or above") {
        captureLogs { logs ->
            errorHandlingTestApp { client ->
                val paths = listOf("/throw/not-found", "/throw/validation", "/throw/conflict")

                paths.map { client.get(it).status } shouldBe
                    listOf(HttpStatusCode.NotFound, HttpStatusCode.BadRequest, HttpStatusCode.Conflict)
            }

            logs.atWarnOrAbove().shouldBeEmpty()
        }
    }

    test("an unmapped exception logs one line at ERROR that names the request and carries the exception") {
        captureLogs { logs ->
            errorHandlingTestApp { client ->
                client.get("/throw/unmapped").status shouldBe HttpStatusCode.InternalServerError
            }

            val unhandled = logs.unhandledErrorsOn("GET /throw/unmapped")
            unhandled.map { it.level } shouldBe listOf(Level.ERROR)
            unhandled.map { it.throwableProxy?.message } shouldBe listOf("boom")
        }
    }

    test("a receive into a type with no serializer logs one line at ERROR, because a server fault has to be seen") {
        captureLogs { logs ->
            errorHandlingTestApp { client ->
                val response = client.postJson("/receive/no-serializer", """{"name":"ada"}""")

                response.status shouldBe HttpStatusCode.InternalServerError
            }

            logs.unhandledErrorsOn("POST /receive/no-serializer").map { it.level } shouldBe listOf(Level.ERROR)
        }
    }

    test("POST /api/v1/auth/login with a truncated JSON body logs nothing at WARN or above and nothing of the body") {
        captureLogs { logs ->
            apiTest { client ->
                val response = client.postJson(LOGIN_PATH, """{"username":"$CLIENT_MARKER""")

                response.status shouldBe HttpStatusCode.BadRequest
            }

            logs.atWarnOrAbove().shouldBeEmpty()
            logs.rendered().filter { CLIENT_MARKER in it }.shouldBeEmpty()
        }
    }
})
