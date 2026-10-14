package com.cwfgw.users

import com.cwfgw.result.Result
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** [clientAddress] must be one the caller cannot choose, or each attempt could claim a fresh address's count. */
data class LoginAttempt(
    val username: String,
    val clientAddress: String,
)

/**
 * Counts failures on the submitted username, never a user row, so an unknown name counts like a known one and the
 * throttle is no enumeration oracle. In memory per instance: Cloud Run's six instances allow six times the limit.
 */
class LoginThrottle(
    private val maxFailures: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
    // Read inside the object expression below, where detekt stops looking for a use.
    @Suppress("UnusedPrivateProperty")
    private val maxTrackedKeys: Int = DEFAULT_MAX_TRACKED_KEYS,
) {
    init {
        require(maxFailures >= 1) { "maxFailures must be at least 1" }
        require(window > Duration.ZERO) { "window must be positive" }
    }

    // Bounded because callers choose the keys; access order keeps a key under attack alive. A monitor, not a
    // concurrent map, because every write is read-then-write, and no block suspends.
    private val recentFailures =
        object : LinkedHashMap<ThrottleKey, List<Instant>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ThrottleKey, List<Instant>>): Boolean =
                size > maxTrackedKeys
        }

    /** Refuses before [verify] runs, so a locked-out caller costs no BCrypt verify and learns nothing from one. */
    suspend fun guard(
        attempt: LoginAttempt,
        verify: suspend () -> Result<User, AuthError>,
    ): Result<User, AuthError> {
        val retryAfter = lockoutRemaining(attempt)
        if (retryAfter != null) return Result.Err(AuthError.TooManyAttempts(retryAfter))

        val outcome = verify()
        when (outcome) {
            is Result.Ok -> forget(ThrottleKey.Username(attempt.username))
            is Result.Err -> recordFailure(attempt)
        }
        return outcome
    }

    private fun lockoutRemaining(attempt: LoginAttempt): Duration? {
        val now = clock.instant()
        return attempt.keys().mapNotNull { key -> lockoutRemaining(key, now) }.maxOrNull()
    }

    private fun lockoutRemaining(
        key: ThrottleKey,
        now: Instant,
    ): Duration? {
        val live = synchronized(recentFailures) { recentFailures[key] }?.liveAt(now).orEmpty()
        val oldestCounted = live.firstOrNull()
        if (live.size < maxFailures || oldestCounted == null) return null
        return Duration.between(now, oldestCounted.plus(window))
    }

    // Keeps only the newest maxFailures stamps: the oldest of those alone decides when the lockout ends.
    private fun recordFailure(attempt: LoginAttempt) {
        val now = clock.instant()
        synchronized(recentFailures) {
            attempt.keys().forEach { key ->
                val live = recentFailures[key]?.liveAt(now).orEmpty()
                recentFailures[key] = (live + now).takeLast(maxFailures)
            }
        }
    }

    private fun forget(key: ThrottleKey) {
        synchronized(recentFailures) { recentFailures.remove(key) }
    }

    private fun List<Instant>.liveAt(now: Instant): List<Instant> = filter { failedAt -> now < failedAt.plus(window) }

    companion object {
        // Each key cost an attacker a BCrypt verify, and the map stays a few MB when full.
        private const val DEFAULT_MAX_TRACKED_KEYS = 10_000
        private const val INITIAL_CAPACITY = 256
        private const val LOAD_FACTOR = 0.75f
    }
}

/** A success clears only [Username]: clearing the address would let one valid account reset a spray's count. */
private sealed interface ThrottleKey {
    /** @see ThrottleKey */
    data class Username(val value: String) : ThrottleKey

    /** @see ThrottleKey */
    data class ClientAddress(val value: String) : ThrottleKey
}

private fun LoginAttempt.keys(): List<ThrottleKey> =
    listOf(ThrottleKey.Username(username), ThrottleKey.ClientAddress(clientAddress))
