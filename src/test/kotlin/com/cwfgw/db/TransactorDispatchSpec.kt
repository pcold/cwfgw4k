package com.cwfgw.db

import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jooq.impl.DSL.condition
import org.jooq.impl.DSL.table
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

private const val CALLER_THREADS = 2
private const val CONCURRENT_CALLS = CALLER_THREADS + 1
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
    // A pool of its own, not a view of Dispatchers.Default: when the calls wedge it, the polling below
    // must still get a thread, or the gate is never opened and the run hangs instead of failing.
    Executors.newFixedThreadPool(CALLER_THREADS).asCoroutineDispatcher().use { caller ->
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
