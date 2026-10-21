package com.cwfgw.users

import com.cwfgw.db.Transactor
import com.cwfgw.jooq.tables.references.USERS
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jooq.exception.DataAccessException

class UserRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = UserRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("the schema refuses a role outside UserRole") {
        val refusal =
            shouldThrow<DataAccessException> {
                postgres.dsl.insertInto(USERS)
                    .set(USERS.USERNAME, "stray")
                    .set(USERS.PASSWORD_HASH, "hash")
                    .set(USERS.ROLE, "superadmin")
                    .execute()
            }

        refusal.message.shouldNotBeNull() shouldContain "users_role_known"
        tx.read { repository.findByUsername("stray") }.shouldBeNull()
    }

    test("the schema admits every UserRole") {
        UserRole.entries.forEach { role ->
            val request = NewUser(username = "user-${role.value}", passwordHash = "hash", role = role)
            val created = tx.update { repository.create(request) }

            tx.read { repository.findById(created.id) }.shouldNotBeNull().role shouldBe role
        }
    }

    test("a role string outside the enum decodes to UserRole.User (least privilege)") {
        UserRole.fromValue("superadmin") shouldBe UserRole.User
    }
})
