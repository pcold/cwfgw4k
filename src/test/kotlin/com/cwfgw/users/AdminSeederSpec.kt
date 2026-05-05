package com.cwfgw.users

import com.cwfgw.config.AuthConfig
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private const val TEST_BCRYPT_COST = 4

private fun authConfig(
    username: String? = "admin",
    password: String? = "hunter2",
): AuthConfig =
    AuthConfig(
        sessionSecret = "test-only",
        sessionMaxAgeSeconds = 3600,
        adminUsername = username,
        adminPassword = password,
        cookieSecure = true,
    )

class AdminSeederSpec : FunSpec({

    test("seedAdminIfEmpty creates an admin user when the table is empty and credentials are configured") {
        val repo = FakeUserRepository()
        val tx = FakeTransactor()
        val authService = AuthService(repo, tx, cost = TEST_BCRYPT_COST)

        seedAdminIfEmpty(authService, repo, tx, authConfig())

        with(noopTransactionContext) {
            val seeded = repo.findByUsername("admin")
            seeded?.role shouldBe UserRole.Admin
            repo.countAll() shouldBe 1L
        }
    }

    test("seedAdminIfEmpty is a no-op when the users table already has rows") {
        val repo = FakeUserRepository()
        val tx = FakeTransactor()
        val authService = AuthService(repo, tx, cost = TEST_BCRYPT_COST)
        with(noopTransactionContext) {
            repo.create(NewUser(username = "existing", passwordHash = "hash"))
        }

        seedAdminIfEmpty(authService, repo, tx, authConfig())

        with(noopTransactionContext) {
            repo.findByUsername("admin") shouldBe null
            repo.countAll() shouldBe 1L
        }
    }

    test("seedAdminIfEmpty skips with a warning when adminUsername is missing — never seeds a default password") {
        val repo = FakeUserRepository()
        val tx = FakeTransactor()
        val authService = AuthService(repo, tx, cost = TEST_BCRYPT_COST)

        seedAdminIfEmpty(authService, repo, tx, authConfig(username = null))

        with(noopTransactionContext) { repo.countAll() } shouldBe 0L
    }

    test("seedAdminIfEmpty skips when adminPassword is blank") {
        val repo = FakeUserRepository()
        val tx = FakeTransactor()
        val authService = AuthService(repo, tx, cost = TEST_BCRYPT_COST)

        seedAdminIfEmpty(authService, repo, tx, authConfig(password = ""))

        with(noopTransactionContext) { repo.countAll() } shouldBe 0L
    }

    test("seeded admin's password verifies via the same AuthService") {
        val repo = FakeUserRepository()
        val tx = FakeTransactor()
        val authService = AuthService(repo, tx, cost = TEST_BCRYPT_COST)

        seedAdminIfEmpty(authService, repo, tx, authConfig(username = "admin", password = "hunter2"))
        val users =
            with(noopTransactionContext) {
                (1L..repo.countAll()).map { repo.findByUsername("admin") }
            }

        users.shouldHaveSize(1)
        // The stored hash actually verifies against the original plaintext (round-trip check).
        val login = authService.login("admin", "hunter2")
        login shouldBe com.cwfgw.result.Result.Ok(users.single()!!)
    }
})
