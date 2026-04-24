package com.cwfgw.users

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FakeUserRepository(
    initial: List<UserWithHash> = emptyList(),
    private val idFactory: () -> UserId = { UserId(UUID.randomUUID()) },
    private val now: () -> java.time.Instant = java.time.Instant::now,
) : UserRepository {
    private val store = ConcurrentHashMap<UserId, UserWithHash>()

    init {
        initial.forEach { entry -> store[entry.user.id] = entry }
    }

    override suspend fun findById(id: UserId): User? = store[id]?.user

    override suspend fun findByUsername(username: String): User? =
        store.values.firstOrNull { it.user.username == username }?.user

    override suspend fun findCredentials(username: String): UserCredentials? =
        store.values
            .firstOrNull { it.user.username == username }
            ?.let { entry -> UserCredentials(user = entry.user, passwordHash = entry.passwordHash) }

    override suspend fun create(request: NewUser): User {
        require(store.values.none { it.user.username == request.username }) {
            "FakeUserRepository: duplicate username '${request.username}'"
        }
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

    override suspend fun countAll(): Long = store.size.toLong()
}

/** Convenience pairing for seeding the fake — keeps the password hash with the user it belongs to. */
data class UserWithHash(
    val user: User,
    val passwordHash: String,
)
