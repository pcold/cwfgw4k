package com.cwfgw.cache

import ch.qos.logback.classic.Level
import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.captureLogs
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

private val INTERVAL = 5.minutes
private const val CONCURRENT_CALLERS = 32
private val SWEEP_FAILURE: String = DataAccessException::class.java.name

class CacheSweepSpec : FunSpec({

    fun sweepOver(
        rows: SweepCountingRepository,
        clock: TestTimeSource,
    ): CacheSweep =
        CacheSweep(
            cache = RequestCache(rows, FakeTransactor(), "sweep-test", defaultTtl = Duration.ofMinutes(5)),
            interval = INTERVAL,
            timeSource = clock,
        )

    test("the first call after boot sweeps") {
        val rows = SweepCountingRepository()
        val sweep = sweepOver(rows, TestTimeSource())

        sweep.sweepIfDue()

        rows.sweeps() shouldBe 1
    }

    test("a call before the interval has passed does not sweep, and one after it does") {
        val rows = SweepCountingRepository()
        val clock = TestTimeSource()
        val sweep = sweepOver(rows, clock)
        sweep.sweepIfDue()

        clock += INTERVAL - 1.milliseconds
        sweep.sweepIfDue()
        rows.sweeps() shouldBe 1

        clock += 1.milliseconds
        sweep.sweepIfDue()
        rows.sweeps() shouldBe 2
    }

    test("concurrent due callers sweep once") {
        val rows = SweepCountingRepository()
        val sweep = sweepOver(rows, TestTimeSource())

        coroutineScope {
            List(CONCURRENT_CALLERS) { async(Dispatchers.IO) { sweep.sweepIfDue() } }.awaitAll()
        }

        rows.sweeps() shouldBe 1
    }

    test("a failed sweep is logged rather than thrown, and the next due call tries again") {
        val rows = SweepCountingRepository(failing = true)
        val clock = TestTimeSource()
        val sweep = sweepOver(rows, clock)

        captureLogs { logs ->
            runBlocking { sweep.sweepIfDue() }

            logs.events().map { Triple(it.level, it.formattedMessage, it.throwableProxy?.className) } shouldContain
                Triple(Level.WARN, "Cache sweep failed; the next request due retries it", SWEEP_FAILURE)
        }
        clock += INTERVAL
        sweep.sweepIfDue()

        rows.sweeps() shouldBe 2
    }
})

/** Counts [deleteExpired] calls; when [failing], each one throws as jOOQ reports a failed statement. */
private class SweepCountingRepository(private val failing: Boolean = false) : CacheRepository {
    private val sweepCount = AtomicInteger()

    fun sweeps(): Int = sweepCount.get()

    context(ctx: TransactionContext)
    override fun get(key: String): String? = null

    context(ctx: TransactionContext)
    override fun generation(): CacheGeneration = CacheGeneration(0)

    context(ctx: WriteTransactionContext)
    override fun put(
        key: String,
        value: String,
        expiresAt: Instant,
        generation: CacheGeneration,
    ): Boolean = true

    context(ctx: WriteTransactionContext)
    override fun deleteExpired(): Int {
        sweepCount.incrementAndGet()
        if (failing) throw DataAccessException("SQL [delete from cache ...]; ERROR", SQLException("ERROR"))
        return 0
    }

    context(ctx: WriteTransactionContext)
    override fun clear(): Int = 0
}
