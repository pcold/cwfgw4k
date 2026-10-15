package com.cwfgw.users

import com.cwfgw.result.Result
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

// Lower BCrypt cost so the test suite stays snappy. 4 is the minimum the at.favre lib accepts.
private const val FAST_COST = 4

private class CountingDispatcher(
    private val delegate: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineDispatcher() {
    val dispatches = AtomicInteger()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatches.incrementAndGet()
        delegate.dispatch(context, block)
    }
}

class AuthServiceSpec : FunSpec({

    test("hashPassword produces a BCrypt \$2a-style hash, not the plaintext") {
        val service = AuthService(FakeUserRepository(), FakeTransactor(), cost = FAST_COST)

        val hash = service.hashPassword("hunter2")

        hash shouldNotBe "hunter2"
        hash shouldStartWith "$2"
    }

    test("login returns the public User when credentials verify") {
        val repo = FakeUserRepository()
        val service = AuthService(repo, FakeTransactor(), cost = FAST_COST)
        val hash = service.hashPassword("hunter2")
        val seeded =
            with(noopTransactionContext) {
                repo.create(NewUser(username = "alice", passwordHash = hash, role = UserRole.Admin))
            }

        val result = service.login("alice", "hunter2").orFail()

        result.id shouldBe seeded.id
        result.role shouldBe UserRole.Admin
    }

    test("login returns InvalidCredentials when the password is wrong") {
        val repo = FakeUserRepository()
        val service = AuthService(repo, FakeTransactor(), cost = FAST_COST)
        val hash = service.hashPassword("hunter2")
        with(noopTransactionContext) {
            repo.create(NewUser(username = "alice", passwordHash = hash))
        }

        service.login("alice", "wrong-password") shouldBe Result.Err(AuthError.InvalidCredentials)
    }

    test("login returns InvalidCredentials when the username is unknown — no enumeration via different errors") {
        val service = AuthService(FakeUserRepository(), FakeTransactor(), cost = FAST_COST)

        service.login("nobody", "any") shouldBe Result.Err(AuthError.InvalidCredentials)
    }

    test("hashing the same password twice produces different hashes (salt is per-call)") {
        val service = AuthService(FakeUserRepository(), FakeTransactor(), cost = FAST_COST)

        val a = service.hashPassword("hunter2")
        val b = service.hashPassword("hunter2")

        a shouldNotBe b
    }

    test("hashing and verifying both run on the hashing dispatcher, off the caller's thread") {
        val repo = FakeUserRepository()
        val hashing = CountingDispatcher()
        val service = AuthService(repo, FakeTransactor(), cost = FAST_COST, hashing = hashing)

        val hash = service.hashPassword("hunter2")
        hashing.dispatches.get() shouldBe 1
        with(noopTransactionContext) { repo.create(NewUser(username = "alice", passwordHash = hash)) }
        service.login("alice", "hunter2").orFail()

        hashing.dispatches.get() shouldBe 2
    }
})
