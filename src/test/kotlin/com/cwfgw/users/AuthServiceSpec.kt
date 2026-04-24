package com.cwfgw.users

import com.cwfgw.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf

// Lower BCrypt cost so the test suite stays snappy. 4 is the minimum the at.favre lib accepts.
private const val FAST_COST = 4

class AuthServiceSpec : FunSpec({

    test("hashPassword produces a BCrypt $2a-style hash, not the plaintext") {
        val service = AuthService(FakeUserRepository(), cost = FAST_COST)

        val hash = service.hashPassword("hunter2")

        hash shouldNotBe "hunter2"
        hash shouldStartWith "$2"
    }

    test("login returns the public User when credentials verify") {
        val repo = FakeUserRepository()
        val service = AuthService(repo, cost = FAST_COST)
        val hash = service.hashPassword("hunter2")
        val seeded = repo.create(NewUser(username = "alice", passwordHash = hash, role = UserRole.Admin))

        val result = service.login("alice", "hunter2").shouldBeInstanceOf<Result.Ok<User>>().value

        result.id shouldBe seeded.id
        result.role shouldBe UserRole.Admin
    }

    test("login returns InvalidCredentials when the password is wrong") {
        val repo = FakeUserRepository()
        val service = AuthService(repo, cost = FAST_COST)
        val hash = service.hashPassword("hunter2")
        repo.create(NewUser(username = "alice", passwordHash = hash))

        service.login("alice", "wrong-password") shouldBe Result.Err(AuthError.InvalidCredentials)
    }

    test("login returns InvalidCredentials when the username is unknown — no enumeration via different errors") {
        val service = AuthService(FakeUserRepository(), cost = FAST_COST)

        service.login("nobody", "any") shouldBe Result.Err(AuthError.InvalidCredentials)
    }

    test("hashing the same password twice produces different hashes (salt is per-call)") {
        val service = AuthService(FakeUserRepository(), cost = FAST_COST)

        val a = service.hashPassword("hunter2")
        val b = service.hashPassword("hunter2")

        a shouldNotBe b
    }
})
