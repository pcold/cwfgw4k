package com.cwfgw.users

import java.time.Duration

/**
 * Typed failure modes a login surfaces alongside success. Variants
 * stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary.
 */
sealed interface AuthError {
    /**
     * Either the username doesn't exist or the password didn't verify. We
     * deliberately collapse those into one variant so the response can't be
     * used to enumerate which usernames are registered — every failed login
     * looks identical to the caller.
     */
    data object InvalidCredentials : AuthError

    /** Answered before the password is verified, so a correct one inside the lockout gets this too. */
    data class TooManyAttempts(val retryAfter: Duration) : AuthError
}
