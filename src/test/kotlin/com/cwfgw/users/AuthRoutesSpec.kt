package com.cwfgw.users

import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

private const val TEST_BCRYPT_COST = 4

private fun withSeededAdmin(
    username: String = "admin",
    password: String = "hunter2",
): ApiFixture.() -> Unit =
    {
        val repo = FakeUserRepository()
        val service = AuthService(repo, cost = TEST_BCRYPT_COST)
        runBlocking {
            val hash = service.hashPassword(password)
            repo.create(NewUser(username = username, passwordHash = hash, role = UserRole.Admin))
        }
        userRepository = repo
        authService = service
    }

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

class AuthRoutesSpec : FunSpec({

    test("POST /auth/login with valid credentials returns 200 with the user and sets a session cookie") {
        apiTest(withSeededAdmin()) { _ ->
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

    test("POST /auth/login with the wrong password returns 401 with the generic credential message") {
        apiTest(withSeededAdmin()) { client ->
            val response =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username = "admin", password = "wrong"))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /auth/login with an unknown username returns the same 401 — no enumeration leak") {
        apiTest(withSeededAdmin()) { client ->
            val response =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username = "nobody", password = "anything"))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /auth/me without a session returns 401") {
        apiTest(withSeededAdmin()) { client ->
            client.get("/api/v1/auth/me").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /auth/me after login returns the authenticated user") {
        apiTest(withSeededAdmin()) { _ ->
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
        apiTest(withSeededAdmin()) { _ ->
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
        val service = AuthService(repo, cost = TEST_BCRYPT_COST)
        runBlocking {
            val hash = service.hashPassword("hunter2")
            repo.create(NewUser(username = "admin", passwordHash = hash, role = UserRole.Admin))
        }
        apiTest(
            {
                userRepository = repo
                authService = service
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
        apiTest(withSeededAdmin()) { client ->
            val response =
                client.get("/api/v1/auth/me") {
                    cookie(name = "cwfgw_session", value = "not-a-valid-signed-session")
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
