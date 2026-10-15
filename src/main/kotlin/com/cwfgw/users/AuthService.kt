package com.cwfgw.users

import at.favre.lib.crypto.bcrypt.BCrypt
import com.cwfgw.db.Transactor
import com.cwfgw.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every failed login answers [AuthError.InvalidCredentials] whether or not the username exists, so the
 * response can't probe for accounts. BCrypt costs ~50–200ms of CPU a call, so it runs on [hashing], never
 * the event loop.
 */
class AuthService(
    private val userRepository: UserRepository,
    private val tx: Transactor,
    private val cost: Int = DEFAULT_COST,
    private val hashing: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun login(
        username: String,
        password: String,
    ): Result<User, AuthError> {
        val credentials =
            tx.get { userRepository.findCredentials(username) }
                ?: return Result.Err(AuthError.InvalidCredentials)
        val verified =
            withContext(hashing) {
                BCrypt.verifyer()
                    .verify(password.toCharArray(), credentials.passwordHash)
                    .verified
            }
        return if (verified) Result.Ok(credentials.user) else Result.Err(AuthError.InvalidCredentials)
    }

    /** Hydrates a [User] for the per-request session validator. */
    suspend fun findById(id: UserId): User? = tx.get { userRepository.findById(id) }

    /**
     * Produce a BCrypt hash for [plain]. Public so the boot-time admin
     * seeder can use the same cost factor as login verification.
     */
    suspend fun hashPassword(plain: String): String =
        withContext(hashing) {
            BCrypt.withDefaults().hashToString(cost, plain.toCharArray())
        }

    companion object {
        /** Default BCrypt work factor. 10 rounds ≈ 50–100ms per hash on common hardware. */
        const val DEFAULT_COST: Int = 10
    }
}
