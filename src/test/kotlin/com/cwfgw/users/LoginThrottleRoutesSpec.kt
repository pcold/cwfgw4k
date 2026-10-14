package com.cwfgw.users

import com.cwfgw.testing.apiTest
import com.cwfgw.testing.seedUser
import io.kotest.core.spec.style.FunSpec
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private const val MAX_FAILURES = 5
private val WINDOW: Duration = Duration.ofMinutes(15)

// The front end appends the peer it saw; everything left of that is the caller's to write.
private const val CLIENT = "203.0.113.7"

private suspend fun HttpClient.login(
    username: String,
    password: String,
    forwardedFor: String? = null,
): HttpResponse =
    post("/api/v1/auth/login") {
        contentType(ContentType.Application.Json)
        forwardedFor?.let { header(HttpHeaders.XForwardedFor, it) }
        setBody(LoginRequest(username = username, password = password))
    }

// Frozen, so Retry-After is the whole window rather than whatever is left of it when the assertion runs.
private fun frozenThrottle(): LoginThrottle =
    LoginThrottle(
        maxFailures = MAX_FAILURES,
        window = WINDOW,
        clock = Clock.fixed(Instant.parse("2026-05-03T00:00:00Z"), ZoneOffset.UTC),
    )

class LoginThrottleRoutesSpec : FunSpec({

    test("past the limit a login is answered 429 with Retry-After, even with the correct password") {
        apiTest({
            seedUser(username = "admin", password = "hunter2", role = UserRole.Admin)
            loginThrottle = frozenThrottle()
        }) { client ->
            repeat(MAX_FAILURES) { client.login("admin", "wrong").status shouldBe HttpStatusCode.Unauthorized }

            val response = client.login("admin", "hunter2")

            response.status shouldBe HttpStatusCode.TooManyRequests
            response.headers[HttpHeaders.RetryAfter] shouldBe WINDOW.seconds.toString()
        }
    }

    test("a spoofed left-most X-Forwarded-For entry does not reset the client address's count") {
        apiTest({
            seedUser(username = "admin", password = "hunter2", role = UserRole.Admin)
            loginThrottle = frozenThrottle()
        }) { client ->
            repeat(MAX_FAILURES) { n ->
                client.login("guess-$n", "wrong", forwardedFor = "10.0.0.$n, $CLIENT").status shouldBe
                    HttpStatusCode.Unauthorized
            }

            client.login("admin", "hunter2", forwardedFor = "10.0.0.99, $CLIENT").status shouldBe
                HttpStatusCode.TooManyRequests
        }
    }

    test("the right-most X-Forwarded-For entry is the client, so a different one is counted apart") {
        apiTest({
            seedUser(username = "admin", password = "hunter2", role = UserRole.Admin)
            loginThrottle = frozenThrottle()
        }) { client ->
            repeat(MAX_FAILURES) { n ->
                client.login("guess-$n", "wrong", forwardedFor = "10.0.0.1, $CLIENT").status shouldBe
                    HttpStatusCode.Unauthorized
            }

            client.login("admin", "hunter2", forwardedFor = "10.0.0.1, 198.51.100.9").status shouldBe HttpStatusCode.OK
        }
    }
})
