package com.cwfgw.users

import com.cwfgw.db.Transactor
import com.cwfgw.jooq.tables.references.USERS
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UserRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = UserRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("an unrecognized role string in the DB decodes to UserRole.User (least privilege)") {
        // Insert directly via jOOQ to bypass the typed write path and simulate a stray DB value.
        postgres.dsl.insertInto(USERS)
            .set(USERS.USERNAME, "stray")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.ROLE, "superadmin")
            .execute()

        tx.read { repository.findByUsername("stray") }.shouldNotBeNull().role shouldBe UserRole.User
    }
})
