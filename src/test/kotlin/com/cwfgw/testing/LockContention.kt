package com.cwfgw.testing

import com.cwfgw.db.LOCK_TIMEOUT_MS
import com.cwfgw.db.Transactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jooq.impl.DSL
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A holder never released lets go before its waiter's lock_timeout would fire. */
private const val HOLD_CEILING_MS: Long = LOCK_TIMEOUT_MS - 2_000

/** Under [HOLD_CEILING_MS], so the probe gives up before a paused holder releases itself. */
private const val LOCK_WAIT_DEADLINE_MS: Long = HOLD_CEILING_MS - 1_000

/**
 * True once a session in this spec's database waits on a lock; false if [pending] finishes first or the deadline
 * passes. The waiter runs through [Transactor], whose connection the spec can't name, so the database scopes the
 * search.
 */
internal suspend fun PostgresHarness.awaitLockWait(pending: Job): Boolean {
    val deadline = Instant.now().plusMillis(LOCK_WAIT_DEADLINE_MS)
    while (!pending.isCompleted && Instant.now().isBefore(deadline)) {
        val waiting =
            dsl.fetchExists(
                DSL.selectOne().from("pg_stat_activity")
                    .where("datname = current_database() and wait_event_type = 'Lock'"),
            )
        if (waiting) return true
        delay(10)
    }
    return false
}

/**
 * Stops one transaction at its first [hold], until [contend] has seen a contender wait on a lock or finish. Later
 * holds pass straight through, so a hook on a repository call the transaction repeats pauses it once.
 */
internal class Pause {
    private val reached = CompletableDeferred<Unit>()
    private val release = CountDownLatch(1)

    fun hold() {
        if (reached.complete(Unit)) release.await(HOLD_CEILING_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * The contender, and whether it was seen waiting on a lock while the paused transaction held. Asserted after the
     * outcomes, so a race that went wrong reports what it produced rather than only that nothing waited.
     */
    suspend fun <T> contend(
        postgres: PostgresHarness,
        contender: () -> Deferred<T>,
    ): Pair<Deferred<T>, Boolean> {
        withTimeout(HOLD_CEILING_MS) { reached.await() }
        val started = contender()
        val waited =
            try {
                postgres.awaitLockWait(started)
            } finally {
                release.countDown()
            }
        return started to waited
    }
}
