package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.result.Result
import com.cwfgw.users.FakeUserRepository
import com.cwfgw.users.NewUser
import com.cwfgw.users.UNIQUE_USERNAME
import com.cwfgw.users.UserCredentials
import com.cwfgw.users.UserId
import com.cwfgw.users.UserRepository
import com.cwfgw.users.UserRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

private val ALICE = NewUser(username = "alice", passwordHash = "hash-secret")

class UserRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", userContract) { ContractSubject(tx, UserRepository(), PostgresSeeds(tx)) }
    runContract("fake", userContract) { ContractSubject(FakeTransactor(), FakeUserRepository(), FakeSeeds) }
})

private val userContract: List<ContractCase<UserRepository>> =
    listOf(
        ContractCase("a created user reads back by id and by username, and an unknown key reads null") {
            val created = tx.update { repository.create(ALICE) }

            created.username shouldBe "alice"
            tx.get { repository.findById(created.id) } shouldBe created
            tx.get { repository.findByUsername("alice") } shouldBe created
            tx.get { repository.findById(UserId(UUID.randomUUID())) }.shouldBeNull()
            tx.get { repository.findByUsername("nobody") }.shouldBeNull()
        },
        ContractCase("a user is created as a plain user unless the request names the admin role") {
            val plain = tx.update { repository.create(ALICE) }
            val admin = tx.update { repository.create(NewUser("root", passwordHash = "hash-2", role = UserRole.Admin)) }

            plain.role shouldBe UserRole.User
            admin.role shouldBe UserRole.Admin
            tx.get { repository.findById(admin.id) }?.role shouldBe UserRole.Admin
        },
        ContractCase("findCredentials answers the stored hash beside the user, and null for an unknown username") {
            val created = tx.update { repository.create(ALICE) }

            tx.get { repository.findCredentials("alice") } shouldBe UserCredentials(created, "hash-secret")
            tx.get { repository.findCredentials("nobody") }.shouldBeNull()
        },
        ContractCase("a second user on a taken username is outraced") {
            tx.update { repository.create(ALICE) }

            val rival = ALICE.copy(passwordHash = "hash-other")
            tx.updateUnlessOutraced(UNIQUE_USERNAME) { repository.create(rival) } shouldBe Result.Err(Outraced)
            tx.get { repository.findCredentials("alice") }?.passwordHash shouldBe "hash-secret"
        },
        ContractCase("countAll counts every user, starting from none") {
            tx.get { repository.countAll() } shouldBe 0L

            tx.update {
                listOf("a", "b", "c").forEach { name -> repository.create(NewUser(name, passwordHash = "h")) }
            }

            tx.get { repository.countAll() } shouldBe 3L
        },
    )
