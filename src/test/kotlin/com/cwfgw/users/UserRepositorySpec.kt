package com.cwfgw.users

import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.exception.DataAccessException
import java.util.UUID

class UserRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = UserRepository(postgres.dsl)

    test("create persists a user with default role 'user' and returns the public shape") {
        val created =
            repository.create(NewUser(username = "alice", passwordHash = "hash-1"))

        created.username shouldBe "alice"
        created.role shouldBe UserRole.User
    }

    test("create with explicit role 'admin' persists that role") {
        val created =
            repository.create(
                NewUser(username = "root", passwordHash = "hash-2", role = UserRole.Admin),
            )

        created.role shouldBe UserRole.Admin
    }

    test("an unrecognized role string in the DB decodes to UserRole.User (least privilege)") {
        // Insert directly via jOOQ to bypass the typed write path and simulate a stray DB value.
        postgres.dsl.insertInto(com.cwfgw.jooq.tables.references.USERS)
            .set(com.cwfgw.jooq.tables.references.USERS.USERNAME, "stray")
            .set(com.cwfgw.jooq.tables.references.USERS.PASSWORD_HASH, "hash")
            .set(com.cwfgw.jooq.tables.references.USERS.ROLE, "superadmin")
            .execute()

        repository.findByUsername("stray").shouldNotBeNull().role shouldBe UserRole.User
    }

    test("create rejects a duplicate username via the UNIQUE constraint") {
        repository.create(NewUser(username = "alice", passwordHash = "hash-1"))

        shouldThrow<DataAccessException> {
            repository.create(NewUser(username = "alice", passwordHash = "hash-other"))
        }
    }

    test("findById returns the persisted user") {
        val created = repository.create(NewUser(username = "alice", passwordHash = "hash-1"))

        repository.findById(created.id).shouldNotBeNull().username shouldBe "alice"
    }

    test("findById returns null for an unknown id") {
        repository.findById(UserId(UUID.randomUUID())).shouldBeNull()
    }

    test("findByUsername returns the persisted user") {
        repository.create(NewUser(username = "alice", passwordHash = "hash-1"))

        repository.findByUsername("alice").shouldNotBeNull().username shouldBe "alice"
    }

    test("findByUsername returns null when no user has that username") {
        repository.findByUsername("nobody").shouldBeNull()
    }

    test("findCredentials returns the public user plus the stored password hash") {
        repository.create(NewUser(username = "alice", passwordHash = "hash-secret"))

        val credentials = repository.findCredentials("alice")

        credentials.shouldNotBeNull()
        credentials.user.username shouldBe "alice"
        credentials.passwordHash shouldBe "hash-secret"
    }

    test("findCredentials returns null when no user has that username") {
        repository.findCredentials("nobody").shouldBeNull()
    }

    test("countAll returns 0 on an empty table") {
        repository.countAll() shouldBe 0L
    }

    test("countAll reflects the number of inserted users") {
        repository.create(NewUser(username = "a", passwordHash = "h"))
        repository.create(NewUser(username = "b", passwordHash = "h"))
        repository.create(NewUser(username = "c", passwordHash = "h"))

        repository.countAll() shouldBe 3L
    }
})
