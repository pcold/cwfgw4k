package com.cwfgw.users

import com.cwfgw.testing.apiTest
import com.cwfgw.testing.seedUser
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

private const val MAX_FAILURES = 5

private suspend fun HttpClient.login(
    username: String,
    password: String,
): HttpResponse =
    post("/api/v1/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username = username, password = password))
    }

class LoginThrottleRoutesSpec : FunSpec({

    test("past the limit a login is answered 429 with Retry-After, even with the correct password") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { client ->
            repeat(MAX_FAILURES) { client.login("admin", "wrong").status shouldBe HttpStatusCode.Unauthorized }

            val response = client.login("admin", "hunter2")

            response.status shouldBe HttpStatusCode.TooManyRequests
            response.headers[HttpHeaders.RetryAfter].shouldNotBeNull()
        }
    }
})
