package com.cwfgw.users

import com.cwfgw.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

private const val MAX_FAILURES = 3
private val WINDOW: Duration = Duration.ofMinutes(15)
private val START: Instant = Instant.parse("2026-05-03T00:00:00Z")

private val ALICE =
    User(id = UserId(UUID(0L, 1L)), username = "alice", role = UserRole.Admin, createdAt = START)

private class StepClock(private var current: Instant = START) : Clock() {
    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = error("not used in tests")
}

private fun attempt(
    username: String = "alice",
    clientAddress: String = "203.0.113.7",
): LoginAttempt = LoginAttempt(username = username, clientAddress = clientAddress)

private suspend fun LoginThrottle.fail(
    attempt: LoginAttempt,
    times: Int = 1,
) = repeat(times) { guard(attempt) { Result.Err(AuthError.InvalidCredentials) } }

private suspend fun LoginThrottle.succeed(attempt: LoginAttempt): Result<User, AuthError> =
    guard(attempt) { Result.Ok(ALICE) }

private suspend fun LoginThrottle.retryAfter(attempt: LoginAttempt): Duration =
    succeed(attempt).shouldBeInstanceOf<Result.Err<AuthError>>()
        .error.shouldBeInstanceOf<AuthError.TooManyAttempts>().retryAfter

private fun throttle(
    clock: Clock = StepClock(),
    maxTrackedKeys: Int = 10_000,
): LoginThrottle = LoginThrottle(MAX_FAILURES, WINDOW, clock, maxTrackedKeys)

class LoginThrottleSpec : FunSpec({

    test("an attempt with no failures behind it reaches the password check") {
        throttle().succeed(attempt()) shouldBe Result.Ok(ALICE)
    }

    test("once the limit has failed, the next attempt is refused without running the password check") {
        val throttle = throttle()
        throttle.fail(attempt(), times = MAX_FAILURES)
        var verified = false

        val outcome = throttle.guard(attempt()) { Result.Ok(ALICE).also { verified = true } }

        outcome.shouldBeInstanceOf<Result.Err<AuthError>>().error.shouldBeInstanceOf<AuthError.TooManyAttempts>()
        verified shouldBe false
    }

    test("one failure short of the limit still reaches the password check") {
        val throttle = throttle()
        throttle.fail(attempt(), times = MAX_FAILURES - 1)

        throttle.succeed(attempt()) shouldBe Result.Ok(ALICE)
    }

    test("retryAfter runs until the oldest counted failure leaves the window") {
        val clock = StepClock()
        val throttle = throttle(clock)
        throttle.fail(attempt())
        clock.advance(Duration.ofMinutes(4))
        throttle.fail(attempt(), times = MAX_FAILURES - 1)

        throttle.retryAfter(attempt()) shouldBe Duration.ofMinutes(11)
    }

    test("the window slides: a failure stops counting once it is a full window old") {
        val clock = StepClock()
        val throttle = throttle(clock)
        throttle.fail(attempt())
        clock.advance(Duration.ofMinutes(4))
        throttle.fail(attempt(), times = MAX_FAILURES - 1)

        clock.advance(Duration.ofMinutes(11))

        throttle.succeed(attempt()) shouldBe Result.Ok(ALICE)
    }

    test("a success clears the username's count") {
        val throttle = throttle()
        throttle.fail(attempt(clientAddress = "198.51.100.1"), times = MAX_FAILURES - 1)
        throttle.succeed(attempt(clientAddress = "198.51.100.2"))

        throttle.fail(attempt(clientAddress = "198.51.100.3"))

        throttle.succeed(attempt(clientAddress = "198.51.100.4")) shouldBe Result.Ok(ALICE)
    }

    test("a success leaves the client address's count standing") {
        val throttle = throttle()
        throttle.fail(attempt(username = "bob"), times = MAX_FAILURES - 1)
        throttle.succeed(attempt(username = "alice"))

        throttle.fail(attempt(username = "carol"))

        throttle.retryAfter(attempt(username = "dave")) shouldBe WINDOW
    }

    test("failures spread over many usernames from one address lock that address") {
        val throttle = throttle()

        (1..MAX_FAILURES).forEach { n -> throttle.fail(attempt(username = "guess-$n")) }

        throttle.retryAfter(attempt(username = "alice")) shouldBe WINDOW
    }

    test("a flood of one-off keys evicts the coldest rather than growing without bound") {
        val throttle = throttle(maxTrackedKeys = 4)
        throttle.fail(attempt(), times = MAX_FAILURES)

        (1..3).forEach { n -> throttle.fail(attempt(username = "throwaway-$n", clientAddress = "flood-$n")) }

        throttle.succeed(attempt(clientAddress = "fresh")) shouldBe Result.Ok(ALICE)
    }

    test("a username still under attack survives a flood, because checking it keeps it warm") {
        val throttle = throttle(maxTrackedKeys = 4)
        throttle.fail(attempt(), times = MAX_FAILURES)

        (1..3).forEach { n ->
            throttle.succeed(attempt(clientAddress = "probe-$n"))
            throttle.fail(attempt(username = "throwaway-$n", clientAddress = "flood-$n"))
        }

        throttle.retryAfter(attempt(clientAddress = "fresh")) shouldBe WINDOW
    }
})
