package com.cwfgw.db

import com.cwfgw.jooq.tables.references.LEAGUES
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.exception.DataAccessException

private const val READ_ONLY_SQL_TRANSACTION = "25006"

/** Each test fails if its entry point is routed to either of the other two modes. */
class TransactorModeSpec : FunSpec({

    val postgres = postgresHarness()
    val transactor = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("read refuses a write") {
        val refused =
            shouldThrow<DataAccessException> {
                transactor.read { insertLeague("Castlewood") }
            }

        refused.sqlState() shouldBe READ_ONLY_SQL_TRANSACTION
    }

    test("get commits each statement on its own, so a throw keeps the writes before it") {
        shouldThrow<IllegalStateException> {
            transactor.get {
                insertLeague("Castlewood")
                error("boom — the insert has already committed")
            }
        }

        postgres.dsl.fetchCount(LEAGUES) shouldBe 1
    }
})

context(ctx: TransactionContext)
private fun insertLeague(name: String) {
    ctx.dsl.insertInto(LEAGUES).set(LEAGUES.NAME, name).execute()
}
