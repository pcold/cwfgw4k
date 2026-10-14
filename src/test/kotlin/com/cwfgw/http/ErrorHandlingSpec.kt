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
import io.ktor.http.HttpHeaders
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
import java.time.Duration
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.get as routingGet
import io.ktor.server.routing.post as routingPost

private const val MALFORMED_BODY = "malformed or invalid request body"
private const val UNSUPPORTED_CONTENT_TYPE = "unsupported or missing content type"
private const val LOGIN_PATH = "/api/v1/auth/login"
private const val CLIENT_MARKER = "zq7-client-marker"
private const val FORBIDDEN_ID = "3f1c2b7e-0d4a-4e8b-9c6f-5a2e1d7b8c90"

/** Unauthorized among them: scan traffic draws 401s in volume, so only a 403 earns a line. */
private val QUIET_DOMAIN_ERRORS =
    mapOf(
        "/throw/not-found" to HttpStatusCode.NotFound,
        "/throw/validation" to HttpStatusCode.BadRequest,
        "/throw/unprocessable" to HttpStatusCode.UnprocessableEntity,
        "/throw/conflict" to HttpStatusCode.Conflict,
        "/throw/refused" to HttpStatusCode.Conflict,
        "/throw/throttled" to HttpStatusCode.TooManyRequests,
        "/throw/unauthorized" to HttpStatusCode.Unauthorized,
    )

/** (seconds, nanos) of the wait → the header. The last overflows any conversion to millis or a +1. */
private val RETRY_AFTER_BY_WAIT =
    mapOf(
        (1L to 400_000L) to "2",
        (0L to 120_000_000L) to "1",
        (0L to 0L) to "1",
        (-5L to 0L) to "1",
        (Long.MAX_VALUE to 999_999_999L) to Long.MAX_VALUE.toString(),
    )

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
                routingGet("/throw/unprocessable") { throw DomainError.Unprocessable("not this pairing") }
                routingGet("/throw/conflict") { throw DomainError.Conflict("already exists") }
                routingGet("/throw/refused") {
                    throw DomainError.Refused("links are locked", code = "tournament_finalized")
                }
                routingGet("/throw/throttled") {
                    throw DomainError.TooManyRequests("slow down", Duration.ofMillis(1_500))
                }
                routingGet("/throw/throttled-for") {
                    val seconds = call.request.queryParameters["seconds"].orEmpty().toLong()
                    val nanos = call.request.queryParameters["nanos"].orEmpty().toLong()
                    throw DomainError.TooManyRequests("slow down", Duration.ofSeconds(seconds, nanos))
                }
                routingGet("/throw/unauthorized") { throw DomainError.Unauthorized("authentication required") }
                routingGet("/throw/forbidden/{id}") { throw DomainError.Forbidden("admin role required") }
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

private fun CapturedLogs.deniedAuthorizations(): List<ILoggingEvent> =
    events().filter { it.formattedMessage.startsWith("cwfgw4k.forbidden ") }

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

    test("DomainError.Unprocessable maps to 422 with the error message in the body") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/unprocessable")

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.bodyAsText() shouldBe """{"error":"not this pairing"}"""
        }
    }

    test("DomainError.Refused maps to 409 with its code beside the error message in the body") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/refused")

            response.status shouldBe HttpStatusCode.Conflict
            response.bodyAsText() shouldBe """{"refusal":"tournament_finalized","error":"links are locked"}"""
        }
    }

    test("DomainError.TooManyRequests maps to 429 with a Retry-After rounded up to whole seconds") {
        errorHandlingTestApp { client ->
            val response = client.get("/throw/throttled")

            response.status shouldBe HttpStatusCode.TooManyRequests
            response.bodyAsText() shouldBe """{"error":"slow down"}"""
            response.headers[HttpHeaders.RetryAfter] shouldBe "2"
        }
    }

    test("Retry-After never names a deadline that has not arrived, nor one sooner than a second away") {
        errorHandlingTestApp { client ->
            RETRY_AFTER_BY_WAIT.forEach { (wait, header) ->
                val (seconds, nanos) = wait
                client.get("/throw/throttled-for?seconds=$seconds&nanos=$nanos")
                    .headers[HttpHeaders.RetryAfter] shouldBe header
            }
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

    test("an empty JSON body is the same 400") {
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

    test("every 4xx DomainError but Forbidden logs nothing at WARN or above") {
        captureLogs { logs ->
            errorHandlingTestApp { client ->
                QUIET_DOMAIN_ERRORS.keys.map { client.get(it).status } shouldBe QUIET_DOMAIN_ERRORS.values.toList()
            }

            logs.atWarnOrAbove().shouldBeEmpty()
        }
    }

    test("DomainError.Forbidden logs one line at WARN naming the method, the normalized route and the reason") {
        captureLogs { logs ->
            errorHandlingTestApp { client ->
                val response = client.get("/throw/forbidden/$FORBIDDEN_ID")

                response.status shouldBe HttpStatusCode.Forbidden
                response.bodyAsText() shouldBe """{"error":"admin role required"}"""
            }

            logs.deniedAuthorizations().map { it.level } shouldBe listOf(Level.WARN)
            logs.deniedAuthorizations().map { it.formattedMessage } shouldBe
                listOf("""cwfgw4k.forbidden method=GET route=/throw/forbidden/:id reason="admin role required"""")
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
