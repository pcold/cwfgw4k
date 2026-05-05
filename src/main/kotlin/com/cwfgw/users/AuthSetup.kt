package com.cwfgw.users

import kotlinx.serialization.Serializable

/** Authenticated user attached to a request by the session-auth provider. */
data class UserPrincipal(val user: User)

/**
 * Session payload stored in the signed cookie. Holds only the user id —
 * everything else is hydrated from the DB on each request so role changes
 * and revocations take effect immediately. The id is stored as a string
 * because the Sessions plugin's default JSON serializer doesn't carry the
 * UUIDSerializer registration the rest of the wire shapes use; keeping it
 * stringly typed here is cheaper than wiring the registration.
 */
@Serializable
data class UserSession(val userId: String)

/**
 * Cookie + signing-key configuration for the Sessions plugin. The secret
 * bytes are an in-memory key derived from [com.cwfgw.config.AuthConfig.sessionSecret]
 * at boot, so the YAML config holds the string form and Main converts once.
 *
 * The signed-cookie design is stateless — sessions survive app restart and
 * work across replicas — at the cost of one operator-managed shared secret.
 * Main fails fast at boot if the secret is blank rather than silently using
 * an empty key.
 */
data class AuthSetup(
    val sessionSecret: ByteArray,
    val sessionMaxAgeSeconds: Long,
    val cookieSecure: Boolean,
)
