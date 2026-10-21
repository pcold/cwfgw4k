package com.cwfgw.users

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.testing.fakeTableOf
import com.cwfgw.testing.uniqueViolation
import java.time.Instant
import java.util.UUID

class FakeUserRepository(
    initial: List<UserWithHash> = emptyList(),
    private val idFactory: () -> UserId = { UserId(UUID.randomUUID()) },
    private val now: () -> Instant = Instant::now,
) : UserRepository {
    private val store = fakeTableOf<UserId, UserWithHash>()

    init {
        initial.forEach { entry -> store[entry.user.id] = entry }
    }

    context(ctx: TransactionContext)
    override fun findById(id: UserId): User? = store[id]?.user

    context(ctx: TransactionContext)
    override fun findByUsername(username: String): User? =
        store.values.firstOrNull { it.user.username == username }?.user

    context(ctx: TransactionContext)
    override fun findCredentials(username: String): UserCredentials? =
        store.values
            .firstOrNull { it.user.username == username }
            ?.let { entry -> UserCredentials(user = entry.user, passwordHash = entry.passwordHash) }

    context(ctx: WriteTransactionContext)
    override fun create(request: NewUser): User {
        if (store.values.any { it.user.username == request.username }) throw uniqueViolation(UNIQUE_USERNAME)
        val user =
            User(
                id = idFactory(),
                username = request.username,
                role = request.role,
                createdAt = now(),
            )
        store[user.id] = UserWithHash(user = user, passwordHash = request.passwordHash)
        return user
    }

    context(ctx: TransactionContext)
    override fun countAll(): Long = store.size.toLong()

    /** Test-only: drop every stored user. Lets specs simulate "user deleted from DB mid-session". */
    fun reset() {
        store.clear()
    }
}

/** Convenience pairing for seeding the fake — keeps the password hash with the user it belongs to. */
data class UserWithHash(
    val user: User,
    val passwordHash: String,
)
