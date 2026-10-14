package com.cwfgw.http

import com.cwfgw.cache.FakeCacheRepository
import com.cwfgw.cache.RequestCache
import com.cwfgw.cache.cachedRespond
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.ApplicationTestBuilder
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

private val PROBE_BODY: String = "compression probe body. ".repeat(100)

private val CACHED_PROBE_ROWS: List<String> = List(100) { index -> "cached probe row $index" }

private const val TEXT_PROBE = "/api/v1/test/text-probe"
private const val CACHED_PROBE = "/api/v1/test/cached-probe"
private const val BODY_SIZE_PROBE = "/api/v1/test/body-size"

/**
 * Text, not JSON: text is on the encoder's allowlist, so this probe is what separates "`/api` is
 * excluded" from "JSON happens not to match".
 */
private fun ApplicationTestBuilder.mountTextProbe() {
    routing {
        get(TEXT_PROBE) { call.respondText(PROBE_BODY, ContentType.Text.Plain) }
    }
}

/** Reports the byte count the handler was handed, which tells "as sent" from "inflated on the way in". */
private fun ApplicationTestBuilder.mountBodySizeProbe() {
    routing {
        post(BODY_SIZE_PROBE) { call.respondText(call.receive<ByteArray>().size.toString()) }
    }
}

private fun ApplicationTestBuilder.mountCachedProbe(
    cache: RequestCache,
    fetches: AtomicInteger,
) {
    routing {
        get(CACHED_PROBE) {
            cachedRespond(cache) {
                fetches.incrementAndGet()
                CACHED_PROBE_ROWS
            }
        }
    }
}

/**
 * The test client advertises no encoding on its own, so a request opts in. Assert on the header, never
 * on body length, which measures whether the client decoded rather than what the server sent.
 * [encodedPathSegments] rather than `get(path)`: the client reads a leading `//` as an authority.
 */
private suspend fun HttpClient.getAccepting(
    path: String,
    encoding: String,
): HttpResponse =
    get {
        url { encodedPathSegments = path.split("/") }
        header(HttpHeaders.AcceptEncoding, encoding)
    }

private val GZIPPED_PROBE_BODY: ByteArray =
    ByteArrayOutputStream()
        .also { sink -> GZIPOutputStream(sink).use { it.write(PROBE_BODY.toByteArray()) } }
        .toByteArray()

class CompressionSpec : FunSpec({
    test("a bundle over the floor comes back gzipped when the client accepts gzip") {
        apiTest { client ->
            val response = client.getAccepting("/assets/test-fixture-large.js", "gzip")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentEncoding] shouldBe "gzip"
            response.headers[HttpHeaders.Vary] shouldContain HttpHeaders.AcceptEncoding
        }
    }

    test("deflate serves a client that asks for it alone") {
        apiTest { client ->
            val response = client.getAccepting("/assets/test-fixture-large.js", "deflate")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentEncoding] shouldBe "deflate"
        }
    }

    test("the same bundle without Accept-Encoding still returns usable bytes") {
        apiTest { client ->
            val response = client.get("/assets/test-fixture-large.js")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
            response.bodyAsText() shouldContain "test-fixture-large"
        }
    }

    test("a stylesheet of a few dozen bytes is under the floor, so it ships raw") {
        apiTest { client ->
            val response = client.getAccepting("/assets/test-fixture.css", "gzip")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
        }
    }

    test("an octet-stream asset over the floor is still left alone") {
        apiTest { client ->
            val response = client.getAccepting("/assets/test-fixture-large.bin", "gzip")

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.OctetStream
            response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
        }
    }

    test("an /api body is not compressed even at a content type the allowlist accepts") {
        apiTest { client ->
            mountTextProbe()
            val response = client.getAccepting(TEXT_PROBE, "gzip")

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Text.Plain
            response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
        }
    }

    // Routing drops empty segments and percent-decodes before it matches, so both reach the text probe.
    // The content type tells "the exclusion held" from "the SPA fallback answered with index.html".
    listOf("//api/v1/test/text-probe", "/%61pi/v1/test/text-probe").forEach { path ->
        test("an /api response under $path, which routing resolves into /api, is not compressed") {
            apiTest { client ->
                mountTextProbe()
                val response = client.getAccepting(path, "gzip")

                response.status shouldBe HttpStatusCode.OK
                response.contentType()?.withoutParameters() shouldBe ContentType.Text.Plain
                response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
            }
        }
    }

    test("a cached /api body is served uncompressed on the miss and on every hit after it") {
        val cache = RequestCache(FakeCacheRepository(), FakeTransactor(), "compression-test", Duration.ofMinutes(5))
        val fetches = AtomicInteger()
        apiTest({ requestCache = cache }) { client ->
            mountCachedProbe(cache, fetches)
            val expectedBody = appJson.encodeToString(CACHED_PROBE_ROWS)

            val gzipMiss = client.getAccepting(CACHED_PROBE, "gzip")
            val plainHit = client.get(CACHED_PROBE)
            val gzipHit = client.getAccepting(CACHED_PROBE, "gzip")

            fetches.get() shouldBe 1
            listOf(gzipMiss, plainHit, gzipHit).forEach { response ->
                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
                response.bodyAsText() shouldBe expectedBody
            }
        }
    }

    test("a gzip-encoded request body reaches its route still compressed") {
        apiTest { client ->
            mountBodySizeProbe()
            val response =
                client.post(BODY_SIZE_PROBE) {
                    header(HttpHeaders.ContentEncoding, "gzip")
                    setBody(GZIPPED_PROBE_BODY)
                }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe GZIPPED_PROBE_BODY.size.toString()
        }
    }
})
