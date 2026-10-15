package com.cwfgw.users

import com.cwfgw.config.AuthConfig
import com.cwfgw.config.ConfigSecret
import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TEST_BCRYPT_COST = 4

/** Two instances booting together: this one counted an empty table before the other's admin committed. */
class AdminSeederRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val users = UserRepository()

    test("a seeder that loses the race to another instance's admin boots without throwing") {
        tx.update { users.create(NewUser(username = "admin", passwordHash = "winner", role = UserRole.Admin)) }
        val config =
            AuthConfig(
                sessionSecret = ConfigSecret("test-only"),
                sessionMaxAgeSeconds = 3600,
                adminUsername = "admin",
                adminPassword = ConfigSecret("hunter2"),
                cookieSecure = true,
            )
        val countedEarly = EmptyCountingUserRepository(users)

        shouldNotThrowAny {
            seedAdminIfEmpty(AuthService(countedEarly, tx, cost = TEST_BCRYPT_COST), countedEarly, tx, config)
        }

        tx.read { users.countAll() } shouldBe 1L
        tx.read { users.findCredentials("admin") }?.passwordHash shouldBe "winner"
    }
})

private class EmptyCountingUserRepository(
    delegate: UserRepository,
) : UserRepository by delegate {
    context(ctx: TransactionContext)
    override fun countAll(): Long = 0
}
