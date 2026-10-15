package com.cwfgw.db

import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.postgresHarness
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jooq.impl.DSL.condition
import org.jooq.impl.DSL.table
import java.sql.Connection
import kotlin.time.Duration.Companion.seconds

private const val CALLER_THREADS = 2
private const val CONCURRENT_CALLS = CALLER_THREADS + 1
private const val CONNECTIONS_HELD_AT_ONCE = CONCURRENT_CALLS + 2
private const val GATE_LOCK_KEY = 4_127L

/**
 * Each call parks its thread in a JDBC statement until the gate opens, so more calls than the caller has
 * threads can only all be parked at once if the [Transactor] moved them off the caller's dispatcher.
 */
class TransactorDispatchSpec : FunSpec({

    val postgres = postgresHarness()
    val transactor = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("get parks more calls in JDBC at once than the caller has threads") {
        postgres.assertCallsOutnumberCallerThreads { block -> transactor.get(block) }
    }

    test("read parks more calls in JDBC at once than the caller has threads") {
        postgres.assertCallsOutnumberCallerThreads { block -> transactor.read(block) }
    }

    test("update parks more calls in JDBC at once than the caller has threads") {
        postgres.assertCallsOutnumberCallerThreads { block -> transactor.update(block) }
    }
})

private suspend fun PostgresHarness.assertCallsOutnumberCallerThreads(
    transact: suspend (context(TransactionContext) () -> Unit) -> Unit,
) {
    check(maxPoolSize >= CONNECTIONS_HELD_AT_ONCE) {
        "a pool of $maxPoolSize cannot hold the gate, $CONCURRENT_CALLS calls and the probe at once"
    }
    dataSource.growTo(CONNECTIONS_HELD_AT_ONCE)
    val caller = Dispatchers.Default.limitedParallelism(CALLER_THREADS)
    dataSource.connection.use { gate ->
        gate.autoCommit = false
        gate.createStatement().use { it.execute("SELECT pg_advisory_xact_lock($GATE_LOCK_KEY)") }
        coroutineScope {
            val calls = List(CONCURRENT_CALLS) { launch(caller) { transact { waitForGate() } } }
            try {
                eventually(5.seconds) { callsWaitingForGate() shouldBe CONCURRENT_CALLS }
            } finally {
                gate.rollback()
            }
            calls.joinAll()
        }
    }
}

/**
 * Of borrowers that arrive together while HikariCP 7 grows a pool, one can wait out its whole `connectionTimeout`
 * with the pool under its maximum, so it grows one borrower at a time first. Moving the probe off the pool only
 * hands that wait to a call.
 */
private fun HikariDataSource.growTo(connections: Int) {
    List(connections) { connection }.forEach(Connection::close)
}

context(ctx: TransactionContext)
private fun waitForGate() {
    ctx.dsl.execute("SELECT pg_advisory_xact_lock_shared($GATE_LOCK_KEY)")
}

private fun PostgresHarness.callsWaitingForGate(): Int =
    dsl.fetchCount(
        table("pg_locks"),
        condition("locktype = 'advisory' AND objid = $GATE_LOCK_KEY AND NOT granted"),
    )
