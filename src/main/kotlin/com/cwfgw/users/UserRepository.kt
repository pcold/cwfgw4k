package com.cwfgw.users

import com.cwfgw.db.TransactionContext
import com.cwfgw.jooq.tables.records.UsersRecord
import com.cwfgw.jooq.tables.references.USERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface UserRepository {
    context(ctx: TransactionContext)
    suspend fun findById(id: UserId): User?

    context(ctx: TransactionContext)
    suspend fun findByUsername(username: String): User?

    /**
     * Lookup that returns the BCrypt hash alongside the public [User]. Only
     * AuthService should call this — every other caller goes through
     * `findById` / `findByUsername` so the hash never reaches them.
     */
    context(ctx: TransactionContext)
    suspend fun findCredentials(username: String): UserCredentials?

    context(ctx: TransactionContext)
    suspend fun create(request: NewUser): User

    /** Used by the boot-time admin seeder to decide whether to insert. */
    context(ctx: TransactionContext)
    suspend fun countAll(): Long
}

data class UserCredentials(
    val user: User,
    val passwordHash: String,
)

fun UserRepository(): UserRepository = JooqUserRepository()

private class JooqUserRepository : UserRepository {
    context(ctx: TransactionContext)
    override suspend fun findById(id: UserId): User? =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toUser)
        }

    context(ctx: TransactionContext)
    override suspend fun findByUsername(username: String): User? =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne()
                ?.let(::toUser)
        }

    context(ctx: TransactionContext)
    override suspend fun findCredentials(username: String): UserCredentials? =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne()
                ?.let { record -> UserCredentials(user = toUser(record), passwordHash = record.passwordHash) }
        }

    context(ctx: TransactionContext)
    override suspend fun create(request: NewUser): User =
        withContext(Dispatchers.IO) {
            val inserted =
                ctx.dsl.insertInto(USERS)
                    .set(USERS.USERNAME, request.username)
                    .set(USERS.PASSWORD_HASH, request.passwordHash)
                    .set(USERS.ROLE, request.role.value)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for users")
            toUser(inserted)
        }

    context(ctx: TransactionContext)
    override suspend fun countAll(): Long =
        withContext(Dispatchers.IO) {
            ctx.dsl.fetchCount(USERS).toLong()
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
