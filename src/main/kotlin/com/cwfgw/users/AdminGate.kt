package com.cwfgw.users

import com.cwfgw.http.DomainError
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext

/**
 * Require the request's authenticated user to have [UserRole.Admin].
 *
 * Call at the top of any data-changing handler that should be admin-only.
 * `authenticate(SESSION_AUTH_NAME) { }` validates the session but doesn't
 * inspect the role — this helper is the role check.
 *
 * Throws [DomainError.Forbidden] for a non-admin principal (mapped to 403),
 * and [DomainError.Unauthorized] when no principal is attached at all
 * (defensive — the auth provider should already block anonymous calls;
 * this catches misconfiguration where a route is gated with `requireAdmin`
 * but not wrapped in `authenticate`).
 */
fun RoutingContext.requireAdmin() {
    val principal = call.principal<UserPrincipal>() ?: throw DomainError.Unauthorized("authentication required")
    if (principal.user.role != UserRole.Admin) {
        throw DomainError.Forbidden("admin role required")
    }
}
