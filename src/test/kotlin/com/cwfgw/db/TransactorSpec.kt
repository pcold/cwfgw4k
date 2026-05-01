package com.cwfgw.db

import com.cwfgw.jooq.tables.references.LEAGUES
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Real-DB coverage for the [Transactor] interface contract.
 *
 * `update` is verified to commit on normal return and roll back on throw —
 * the rollback invariant is the load-bearing reason it exists. `read` is
 * verified to give the block a single consistent snapshot, even when a
 * concurrent writer commits between statements; that snapshot guarantee is
 * what the `REPEATABLE READ READ ONLY` isolation level buys us.
 */
class TransactorSpec : FunSpec({

    val postgres = postgresHarness()
    val transactor = Transactor(postgres.dsl)

    test("update commits the block's writes when it returns normally") {
        transactor.update { insertLeague("Castlewood") }

        transactor.read { leagueNames() } shouldBe listOf("Castlewood")
    }

    test("update rolls back every write in the block when the block throws") {
        shouldThrow<IllegalStateException> {
            transactor.update {
                insertLeague("First")
                insertLeague("Second")
                error("boom — should roll both inserts back")
            }
        }

        transactor.read { countLeagues() } shouldBe 0
    }

    test("read sees a consistent snapshot across statements even when a writer commits mid-block") {
        runBlocking {
            val readStarted = CompletableDeferred<Unit>()
            val writerCommitted = CompletableDeferred<Unit>()

            coroutineScope {
                val readJob =
                    async(Dispatchers.IO) {
                        transactor.read {
                            val before = countLeagues()
                            readStarted.complete(Unit)
                            writerCommitted.await()
                            val after = countLeagues()
                            before to after
                        }
                    }

                readStarted.await()
                postgres.dsl.insertInto(LEAGUES).set(LEAGUES.NAME, "concurrent").execute()
                writerCommitted.complete(Unit)

                val (before, after) = readJob.await()
                before shouldBe 0
                after shouldBe 0
            }
        }

        // Sanity: outside the read snapshot, the writer's commit is visible.
        transactor.read { countLeagues() } shouldBe 1
    }
})

context(ctx: TransactionContext)
private fun countLeagues(): Int = ctx.dsl.fetchCount(LEAGUES)

context(ctx: TransactionContext)
private fun leagueNames(): List<String?> =
    ctx.dsl.select(LEAGUES.NAME).from(LEAGUES).fetch(LEAGUES.NAME)

context(ctx: TransactionContext)
private fun insertLeague(name: String) {
    ctx.dsl.insertInto(LEAGUES).set(LEAGUES.NAME, name).execute()
}
