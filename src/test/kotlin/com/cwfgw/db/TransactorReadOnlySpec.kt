package com.cwfgw.db

import com.cwfgw.jooq.tables.references.LEAGUES
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.exception.DataAccessException

/** SQLSTATE `read_only_sql_transaction`. */
private const val READ_ONLY_SQL_TRANSACTION = "25006"

class TransactorReadOnlySpec : FunSpec({

    val postgres = postgresHarness()
    val transactor = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("read refuses a write, and nothing it attempted lands") {
        val refused =
            shouldThrow<DataAccessException> {
                transactor.read { insertLeague("Castlewood") }
            }

        refused.sqlState() shouldBe READ_ONLY_SQL_TRANSACTION
        postgres.dsl.fetchCount(LEAGUES) shouldBe 0
    }
})

context(ctx: TransactionContext)
private fun insertLeague(name: String) {
    ctx.dsl.insertInto(LEAGUES).set(LEAGUES.NAME, name).execute()
}
