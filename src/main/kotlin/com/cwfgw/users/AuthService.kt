package com.cwfgw.users

import at.favre.lib.crypto.bcrypt.BCrypt
import com.cwfgw.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Password hashing + login verification. Owns the BCrypt cost factor and
 * the username-enumeration safety: every failed login returns the same
 * [AuthError.InvalidCredentials] regardless of whether the username
 * existed, so an attacker can't probe for accounts.
 *
 * BCrypt ops are CPU-bound on a single thread and take ~50–200ms at the
 * default cost — fine for a login endpoint, but they belong on
 * [Dispatchers.Default] (or IO; either keeps them off the event loop).
 */
class AuthService(
    private val userRepository: UserRepository,
    private val cost: Int = DEFAULT_COST,
) {
    suspend fun login(
        username: String,
        password: String,
    ): Result<User, AuthError> {
        val credentials =
            userRepository.findCredentials(username) ?: return Result.Err(AuthError.InvalidCredentials)
        val verified =
            withContext(Dispatchers.Default) {
                BCrypt.verifyer()
                    .verify(password.toCharArray(), credentials.passwordHash)
                    .verified
            }
        return if (verified) Result.Ok(credentials.user) else Result.Err(AuthError.InvalidCredentials)
    }

    /**
     * Produce a BCrypt hash for [plain]. Public so the boot-time admin
     * seeder can use the same cost factor as login verification.
     */
    suspend fun hashPassword(plain: String): String =
        withContext(Dispatchers.Default) {
            BCrypt.withDefaults().hashToString(cost, plain.toCharArray())
        }

    companion object {
        /** Default BCrypt work factor. 10 rounds ≈ 50–100ms per hash on common hardware. */
        const val DEFAULT_COST: Int = 10
    }
}
