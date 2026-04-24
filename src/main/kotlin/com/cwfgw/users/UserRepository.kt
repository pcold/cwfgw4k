package com.cwfgw.users

import com.cwfgw.jooq.tables.records.UsersRecord
import com.cwfgw.jooq.tables.references.USERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

interface UserRepository {
    suspend fun findById(id: UserId): User?

    suspend fun findByUsername(username: String): User?

    /**
     * Lookup that returns the BCrypt hash alongside the public [User]. Only
     * AuthService should call this — every other caller goes through
     * `findById` / `findByUsername` so the hash never reaches them.
     */
    suspend fun findCredentials(username: String): UserCredentials?

    suspend fun create(request: NewUser): User

    /** Used by the boot-time admin seeder to decide whether to insert. */
    suspend fun countAll(): Long
}

data class UserCredentials(
    val user: User,
    val passwordHash: String,
)

fun UserRepository(dsl: DSLContext): UserRepository = JooqUserRepository(dsl)

private class JooqUserRepository(private val dsl: DSLContext) : UserRepository {
    override suspend fun findById(id: UserId): User? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toUser)
        }

    override suspend fun findByUsername(username: String): User? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne()
                ?.let(::toUser)
        }

    override suspend fun findCredentials(username: String): UserCredentials? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne()
                ?.let { record -> UserCredentials(user = toUser(record), passwordHash = record.passwordHash) }
        }

    override suspend fun create(request: NewUser): User =
        withContext(Dispatchers.IO) {
            val inserted =
                dsl.insertInto(USERS)
                    .set(USERS.USERNAME, request.username)
                    .set(USERS.PASSWORD_HASH, request.passwordHash)
                    .set(USERS.ROLE, request.role.value)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for users")
            toUser(inserted)
        }

    override suspend fun countAll(): Long =
        withContext(Dispatchers.IO) {
            dsl.fetchCount(USERS).toLong()
        }

    private fun toUser(record: UsersRecord): User =
        User(
            id = UserId(checkNotNull(record.id) { "users.id is NOT NULL but returned null" }),
            username = record.username,
            role =
                UserRole.fromValue(
                    checkNotNull(record.role) { "users.role is NOT NULL but returned null" },
                ),
            createdAt =
                checkNotNull(record.createdAt) {
                    "users.created_at is NOT NULL but returned null"
                }.toInstant(),
        )
}
