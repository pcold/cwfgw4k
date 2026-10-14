package com.cwfgw.users

import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.TEST_ADMIN_PASSWORD
import com.cwfgw.testing.TEST_ADMIN_USERNAME
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.seedUser
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.setCookie
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

private const val TEST_BCRYPT_COST = 4

@OptIn(ExperimentalSerializationApi::class)
private val TEST_JSON =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

private fun ApplicationTestBuilder.cookieClient(): HttpClient =
    createClient {
        install(ContentNegotiation) {
            json(TEST_JSON)
        }
        install(HttpCookies)
    }

// Not Ktor's default of 7 days, so a cookie that ignores AuthSetup cannot match it.
private const val CONFIGURED_MAX_AGE_SECONDS = 5_400L

// authenticatedApiTest seeds the admin but keeps its own login response, so the cookie is read off a second login.
private suspend fun HttpClient.sessionCookieFromLogin(): Cookie {
    val response =
        post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = TEST_ADMIN_USERNAME, password = TEST_ADMIN_PASSWORD))
        }
    response.status shouldBe HttpStatusCode.OK
    return response.setCookie().singleOrNull { it.name == "cwfgw_session" }.shouldNotBeNull()
}

class AuthRoutesSpec : FunSpec({

    test("POST /auth/login with valid credentials returns 200 with the user and sets a session cookie") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { _ ->
            val client = cookieClient()
            val response =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username = "admin", password = "hunter2"))
                }

            response.status shouldBe HttpStatusCode.OK
            val user = response.body<User>()
            user.username shouldBe "admin"
            user.role shouldBe UserRole.Admin
            response.headers.getAll("Set-Cookie")?.joinToString().orEmpty() shouldContain "cwfgw_session"
        }
    }

    test("POST /auth/login scopes the session cookie to Path=/") {
        authenticatedApiTest { client ->
            client.sessionCookieFromLogin().path shouldBe "/"
        }
    }

    test("POST /auth/login marks the session cookie HttpOnly") {
        authenticatedApiTest { client ->
            client.sessionCookieFromLogin().httpOnly shouldBe true
        }
    }

    test("POST /auth/login sets the session cookie SameSite=Lax") {
        authenticatedApiTest { client ->
            client.sessionCookieFromLogin().extensions["SameSite"] shouldBe "Lax"
        }
    }

    test("POST /auth/login gives the session cookie the configured Max-Age") {
        authenticatedApiTest({
            authSetup = authSetup.copy(sessionMaxAgeSeconds = CONFIGURED_MAX_AGE_SECONDS)
        }) { client ->
            client.sessionCookieFromLogin().maxAge shouldBe CONFIGURED_MAX_AGE_SECONDS.toInt()
        }
    }

    test("POST /auth/login marks the session cookie Secure when cookieSecure is on") {
        authenticatedApiTest({ authSetup = authSetup.copy(cookieSecure = true) }) { client ->
            client.sessionCookieFromLogin().secure shouldBe true
        }
    }

    test("POST /auth/login leaves Secure off the session cookie when cookieSecure is off") {
        authenticatedApiTest({ authSetup = authSetup.copy(cookieSecure = false) }) { client ->
            client.sessionCookieFromLogin().secure shouldBe false
        }
    }

    test("POST /auth/login with the wrong password returns 401 with the generic credential message") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { client ->
            val response =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username = "admin", password = "wrong"))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /auth/login with an unknown username returns the same 401 — no enumeration leak") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { client ->
            val response =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username = "nobody", password = "anything"))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /auth/me without a session returns 401") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { client ->
            client.get("/api/v1/auth/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /auth/me after login returns the authenticated user") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { _ ->
            val client = cookieClient()
            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "admin", password = "hunter2"))
            }

            val response = client.get("/api/v1/auth/me")

            response.status shouldBe HttpStatusCode.OK
            response.body<User>().username shouldBe "admin"
        }
    }

    test("POST /auth/logout invalidates the session so subsequent /me returns 401") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { _ ->
            val client = cookieClient()
            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "admin", password = "hunter2"))
            }
            client.get("/api/v1/auth/me").status shouldBe HttpStatusCode.OK

            client.post("/api/v1/auth/logout").status shouldBe HttpStatusCode.NoContent

            client.get("/api/v1/auth/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("a user deleted from the DB mid-session can no longer hit /me — revocation is immediate") {
        val repo = FakeUserRepository()
        apiTest(
            {
                userRepository = repo
                authService = AuthService(repo, FakeTransactor(), cost = TEST_BCRYPT_COST)
                seedUser(username = "admin", password = "hunter2", role = UserRole.Admin)
            },
        ) { _ ->
            val client = cookieClient()
            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "admin", password = "hunter2"))
            }
            client.get("/api/v1/auth/me").status shouldBe HttpStatusCode.OK

            // Admin gets removed from the DB while the cookie is still HMAC-valid.
            repo.reset()

            client.get("/api/v1/auth/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("a tampered cookie value fails HMAC verification and is treated as no session") {
        apiTest({ seedUser(username = "admin", password = "hunter2", role = UserRole.Admin) }) { client ->
            val response =
                client.get("/api/v1/auth/me") {
                    cookie(name = "cwfgw_session", value = "not-a-valid-signed-session")
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
