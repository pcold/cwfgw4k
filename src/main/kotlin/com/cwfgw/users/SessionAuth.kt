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
            // `Secure` so the cookie only travels over HTTPS — prevents
            // accidental leakage if any path on the domain ever hits HTTP.
            // Configurable via AUTH_COOKIE_SECURE so local dev against
            // http://localhost:8080 still works.
            cookie.secure = authSetup.cookieSecure
            // `SameSite=Lax` blocks cross-site form POSTs from carrying the
            // cookie; same-site navigation (top-level GETs) still attaches
            // it so login redirects keep working. Lax over Strict because
            // the post-login bounce is a same-origin GET that needs the
            // session to be present.
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
