package com.cwfgw.users

import com.cwfgw.result.Result
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

/** [clientAddress] must be one the caller cannot choose, or each attempt could claim a fresh address's count. */
data class LoginAttempt(
    val username: String,
    val clientAddress: String,
)

/**
 * Counts failures on the submitted username, never a user row, so an unknown name counts like a known one and the
 * throttle is no enumeration oracle. In memory per instance, so N instances allow N times the limit.
 */
class LoginThrottle(
    private val maxFailures: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
    private val maxTrackedKeys: Int = DEFAULT_MAX_TRACKED_KEYS,
) {
    init {
        require(maxFailures >= 1) { "maxFailures must be at least 1" }
        require(window > Duration.ZERO) { "window must be positive" }
        require(maxTrackedKeys >= 2) { "maxTrackedKeys must hold one attempt's two keys" }
    }

    // One monitor over the whole map, not a concurrent map: admission checks and counts two keys as one step, and
    // nothing inside it suspends.
    private val recentFailures = LinkedHashMap<ThrottleKey, List<Instant>>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER)

    /**
     * Refuses before [verify] runs, so a locked-out caller costs no BCrypt verify. An attempt counts as a failure from
     * admission until it succeeds, so concurrent attempts can't all pass a check none has yet counted; one that throws
     * stays counted.
     */
    suspend fun guard(
        attempt: LoginAttempt,
        verify: suspend () -> Result<User, AuthError>,
    ): Result<User, AuthError> {
        val keys = attempt.keys()
        val admittedAt =
            when (val admission = admit(keys)) {
                is Result.Err -> return admission
                is Result.Ok -> admission.value
            }
        val outcome = verify()
        if (outcome is Result.Ok) release(keys, admittedAt)
        return outcome
    }

    private fun admit(keys: AttemptKeys): Result<Instant, AuthError.TooManyAttempts> =
        synchronized(recentFailures) {
            val now = clock.instant()
            val retryAfter = keys.both.mapNotNull { key -> lockoutRemaining(key, now) }.maxOrNull()
            if (retryAfter != null) {
                Result.Err(AuthError.TooManyAttempts(retryAfter))
            } else {
                keys.both.forEach { key -> recentFailures[key] = liveFailures(key, now) + now }
                evictOverflow(keys, now)
                Result.Ok(now)
            }
        }

    private fun lockoutRemaining(
        key: ThrottleKey,
        now: Instant,
    ): Duration? {
        val live = liveFailures(key, now)
        val oldestCounted = live.minOrNull()
        if (live.size < maxFailures || oldestCounted == null) return null
        return Duration.between(now, oldestCounted.plus(window))
    }

    // Clears the username but gives back only this attempt's stamp on the address: clearing the address would let one
    // valid account reset a spray's count.
    private fun release(
        keys: AttemptKeys,
        admittedAt: Instant,
    ) {
        synchronized(recentFailures) {
            recentFailures.remove(keys.username)
            recentFailures.computeIfPresent(keys.clientAddress) { _, stamps -> (stamps - admittedAt).ifEmpty { null } }
        }
    }

    // Never this attempt's own keys, or a full map would leave it uncounted; a locked key only once no unlocked one is
    // left, so a flood of fresh names that never touches a locked key can't reset it.
    private fun evictOverflow(
        keys: AttemptKeys,
        now: Instant,
    ) {
        while (recentFailures.size > maxTrackedKeys) {
            val others = recentFailures.entries.asSequence().filter { (key, _) -> key !in keys.both }
            val coldest =
                others.firstOrNull { (_, stamps) -> stamps.liveAt(now).size < maxFailures } ?: others.firstOrNull()
                    ?: return
            recentFailures.remove(coldest.key)
        }
    }

    private fun liveFailures(
        key: ThrottleKey,
        now: Instant,
    ): List<Instant> = recentFailures[key]?.liveAt(now).orEmpty()

    private fun List<Instant>.liveAt(now: Instant): List<Instant> = filter { failedAt -> now < failedAt.plus(window) }

    companion object {
        // A few MB when full. An unknown username skips BCrypt, so a spray of fresh names is cheap to send.
        private const val DEFAULT_MAX_TRACKED_KEYS = 10_000
        private const val INITIAL_CAPACITY = 256
        private const val LOAD_FACTOR = 0.75f
        private const val ACCESS_ORDER = true
    }
}

/** Holds digests: the caller chooses each value's length, and a key outlives the request. */
private sealed interface ThrottleKey {
    /** @see ThrottleKey */
    data class Username(val digest: String) : ThrottleKey

    /** @see ThrottleKey */
    data class ClientAddress(val digest: String) : ThrottleKey
}

private data class AttemptKeys(
    val username: ThrottleKey.Username,
    val clientAddress: ThrottleKey.ClientAddress,
) {
    val both: List<ThrottleKey> get() = listOf(username, clientAddress)
}

private fun LoginAttempt.keys(): AttemptKeys =
    AttemptKeys(
        username = ThrottleKey.Username(sha256(username)),
        clientAddress = ThrottleKey.ClientAddress(sha256(clientAddress)),
    )

private fun sha256(value: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
