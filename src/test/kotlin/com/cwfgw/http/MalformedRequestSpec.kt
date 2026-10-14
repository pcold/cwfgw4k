package com.cwfgw.http

import ch.qos.logback.classic.Level
import com.cwfgw.testing.CapturedLogs
import com.cwfgw.testing.captureLogs
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.net.Socket
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

private const val MALFORMED_URL = """{"error":"malformed request URL"}"""
private const val MALFORMED_HEADER = """{"error":"malformed Accept or Content-Type header"}"""
private const val BAD_PARAMETER = """{"error":"missing or invalid request parameter"}"""
private const val LOOPBACK = "127.0.0.1"

@Serializable
private data class Greeting(val name: String)

private class NoConversion

/** `ContentType.parse` refuses an empty subtype, and the client sends the label without parsing it. */
private class MislabeledBody(private val text: String) : OutgoingContent.ByteArrayContent() {
    override val contentType: ContentType = ContentType("nonsense", "")

    override fun bytes(): ByteArray = text.toByteArray()
}

/** The HTTP stack `module()` installs, so the request log also meets a path it cannot decode. */
private fun Application.malformedRequestModule() {
    install(ServerContentNegotiation) { json(appJson) }
    installRequestLogging()
    installErrorHandling()
    routing {
        get("/greeting") { call.respond(Greeting("ada")) }
        get("/search") { call.respond(Greeting(call.request.queryParameters["name"].orEmpty())) }
        post("/greeting") { call.respond(call.receive<Greeting>()) }
        get("/required") { call.respond(Greeting(call.request.queryParameters.getOrFail("name"))) }
        get("/converted") {
            val count = call.request.queryParameters.getOrFail<Int>("count")
            call.respond(Greeting("x".repeat(count)))
        }
        get("/unconvertible") {
            call.request.queryParameters.getOrFail<NoConversion>("name")
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun malformedRequestTestApp(block: suspend (HttpClient) -> Unit) =
    testApplication {
        application { malformedRequestModule() }
        block(client)
    }

private data class RawResponse(val status: Int, val body: String)

/**
 * The Ktor client decodes a URL while building the request and throws on one it cannot, so an undecodable
 * target can only be written to a socket. Netty, not the test engine: production's engine decodes a query its own way.
 */
private fun rawGet(target: String): RawResponse {
    val server = embeddedServer(Netty, port = 0, host = LOOPBACK) { malformedRequestModule() }.start(wait = false)
    try {
        val connectors = runBlocking { server.engine.resolvedConnectors() }
        val port = connectors.firstOrNull()?.port ?: error("Netty bound no connector")
        return Socket(LOOPBACK, port).use { socket ->
            val request = "GET $target HTTP/1.1\r\nHost: $LOOPBACK\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray())
            val response = socket.getInputStream().readBytes().decodeToString()
            RawResponse(
                status = response.substringAfter(' ').substringBefore(' ').toInt(),
                body = response.substringAfter("\r\n\r\n"),
            )
        }
    } finally {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }
}

private data class ClientFault(
    val description: String,
    val body: String,
    val send: suspend (HttpClient) -> HttpResponse,
)

private val clientFaults =
    listOf(
        ClientFault("an unparseable Accept header", MALFORMED_HEADER) { client ->
            client.get("/greeting") { header(HttpHeaders.Accept, "nonsense") }
        },
        ClientFault("an unparseable Content-Type header", MALFORMED_HEADER) { client ->
            client.post("/greeting") { setBody(MislabeledBody("""{"name":"ada"}""")) }
        },
        ClientFault("a missing required parameter", BAD_PARAMETER) { client -> client.get("/required") },
        ClientFault("a parameter that does not convert", BAD_PARAMETER) { client -> client.get("/converted?count=x") },
    )

private val undecodableTargets = mapOf("an undecodable path" to "/%ZZ", "an undecodable query" to "/search?name=%ZZ")

private fun CapturedLogs.atWarnOrAbove(): List<String> =
    events()
        .filter { it.level.isGreaterOrEqual(Level.WARN) }
        .map { "${it.level} ${it.loggerName} - ${it.formattedMessage}" }

class MalformedRequestSpec : FunSpec({

    clientFaults.forEach { fault ->
        test("${fault.description} is a 400 with a fixed message, logged at nothing above INFO") {
            captureLogs { logs ->
                malformedRequestTestApp { client ->
                    val response = fault.send(client)

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.bodyAsText() shouldBe fault.body
                }

                logs.atWarnOrAbove().shouldBeEmpty()
            }
        }
    }

    undecodableTargets.forEach { (description, target) ->
        test("$description is a 400 with a fixed message, logged at nothing above INFO") {
            captureLogs { logs ->
                val response = rawGet(target)

                response.status shouldBe HttpStatusCode.BadRequest.value
                response.body shouldBe MALFORMED_URL
                logs.atWarnOrAbove().shouldBeEmpty()
            }
        }
    }

    test("a parameter asked for as a type with no conversion is a 500 logged at ERROR, because the fault is ours") {
        captureLogs { logs ->
            malformedRequestTestApp { client ->
                client.get("/unconvertible?name=ada").status shouldBe HttpStatusCode.InternalServerError
            }

            logs.events().filter { it.formattedMessage == "Unhandled error on GET /unconvertible" }
                .map { it.level } shouldBe listOf(Level.ERROR)
        }
    }
})
