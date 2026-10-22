package com.cwfgw.http

import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.File

class StaticRoutesSpec : FunSpec({
    test("GET / serves the index html with no-cache so users never pin to a stale shell") {
        apiTest { client ->
            val response = client.get("/")

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Text.Html
            response.headers[HttpHeaders.CacheControl] shouldBe "no-cache"
            response.bodyAsText() shouldContain "test-fixture-index"
        }
    }

    test("GET /assets/<file> serves the bundled asset with immutable cache headers") {
        apiTest { client ->
            val response = client.get("/assets/test-fixture-asset.js")

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.JavaScript
            response.headers[HttpHeaders.CacheControl] shouldBe "public, max-age=31536000, immutable"
        }
    }

    test("GET /assets/missing returns 404 instead of falling through to the SPA shell") {
        apiTest { client ->
            val response = client.get("/assets/missing.js")

            response.status shouldBe HttpStatusCode.NotFound
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
            response.body<ErrorBody>().error shouldBe "asset not found"
        }
    }

    context("a root file is served as itself for a day, not immutably, since its name never changes") {
        withData(
            nameFn = { (path, _) -> path },
            "/favicon.ico" to ContentType.Image.XIcon,
            "/favicon.svg" to ContentType.Image.SVG,
            "/icon-192.png" to ContentType.Image.PNG,
            "/manifest.webmanifest" to ContentType("application", "manifest+json"),
        ) { (path, contentType) ->
            apiTest { client ->
                val response = client.get(path)

                response.status shouldBe HttpStatusCode.OK
                response.contentType()?.withoutParameters() shouldBe contentType
                response.headers[HttpHeaders.CacheControl] shouldBe "public, max-age=86400"
                response.bodyAsText() shouldContain "test-fixture-root-file"
            }
        }
    }

    test("every file Vite copies from ui/public to the bundle root is routed") {
        // Finder's .DS_Store is no file of ours; git ignores it, so a clean checkout never ships one.
        val published = File("ui/public").list().orEmpty().filterNot { name -> name.startsWith(".") }

        ROOT_FILES shouldContainExactlyInAnyOrder published
    }

    test("GET /admin returns the SPA shell so deep links survive a refresh") {
        apiTest { client ->
            val response = client.get("/admin")

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Text.Html
            response.headers[HttpHeaders.CacheControl] shouldBe "no-cache"
        }
    }

    test("GET /api/v1/nonexistent returns 404 — SPA fallback must not mask real API misses") {
        apiTest { client ->
            val response = client.get("/api/v1/nonexistent")

            response.status shouldBe HttpStatusCode.NotFound
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
            response.body<ErrorBody>().error shouldBe "route not found"
        }
    }
})
