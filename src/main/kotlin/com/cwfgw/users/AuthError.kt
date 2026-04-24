package com.cwfgw.users

/**
 * Typed failure modes the auth service surfaces alongside success. Variants
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
}
