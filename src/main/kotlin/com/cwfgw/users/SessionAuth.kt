package com.cwfgw.users

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

internal fun Application.installSessionAuth(
    authSetup: AuthSetup,
    authService: AuthService,
) {
    install(Sessions) {
        cookie<UserSession>("cwfgw_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            // Off only through AUTH_COOKIE_SECURE, for local dev against http://localhost:8080.
            cookie.secure = authSetup.cookieSecure
            // Lax, not Strict: Strict would land a link followed in from another site logged out.
            // Lax still keeps the cookie off cross-site form POSTs.
            cookie.extensions["SameSite"] = "Lax"
            cookie.maxAgeInSeconds = authSetup.sessionMaxAgeSeconds
            transform(SessionTransportTransformerMessageAuthentication(authSetup.sessionSecret))
        }
    }
    install(Authentication) {
        session<UserSession>(SESSION_AUTH_NAME) {
            validate { session ->
                session.userId.toUserId()?.let { id ->
                    authService.findById(id)?.let(::UserPrincipal)
                }
            }
        }
    }
}
