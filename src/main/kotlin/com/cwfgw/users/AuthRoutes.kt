package com.cwfgw.users

import com.cwfgw.http.DomainError
import com.cwfgw.result.Result
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable

const val SESSION_AUTH_NAME: String = "session"

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/login") { login(authService) }
        post("/logout") { logout() }
        authenticate(SESSION_AUTH_NAME) {
            get("/me") { me() }
        }
    }
}

private suspend fun RoutingContext.login(authService: AuthService) {
    val request = call.receive<LoginRequest>()
    val user = authService.login(request.username, request.password).orThrow()
    call.sessions.set(UserSession(userId = user.id.value.toString()))
    call.respond(user)
}

private suspend fun RoutingContext.logout() {
    call.sessions.clear<UserSession>()
    call.respond(HttpStatusCode.NoContent)
}

private suspend fun RoutingContext.me() {
    val principal =
        call.principal<UserPrincipal>()
            ?: throw DomainError.Unauthorized("no authenticated user")
    call.respond(principal.user)
}

private fun AuthError.toDomainError(): DomainError =
    when (this) {
        AuthError.InvalidCredentials -> DomainError.Unauthorized("invalid username or password")
    }

private fun <T> Result<T, AuthError>.orThrow(): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> throw error.toDomainError()
    }
