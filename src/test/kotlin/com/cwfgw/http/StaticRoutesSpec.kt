package com.cwfgw.http

import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

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
        }
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
        }
    }
})
