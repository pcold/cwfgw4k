package com.cwfgw.testing

import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private const val SCRATCH_TABLE = "harness_scratch"

class PostgresHarnessSpec : FunSpec({
    val postgres = postgresHarness()

    test("two specs get separate databases") {
        withSecondSpec { second ->
            second.databaseName() shouldNotBe postgres.databaseName()
        }
    }

    test("two specs share one Postgres cluster") {
        withSecondSpec { second ->
            second.clusterIdentifier() shouldBe postgres.clusterIdentifier()
        }
    }

    test("a table one spec creates is absent from the database of a spec that starts later") {
        postgres.dsl.execute("CREATE TABLE $SCRATCH_TABLE (id INT)")

        withSecondSpec { second ->
            second.hasTable(SCRATCH_TABLE) shouldBe false
        }
        postgres.hasTable(SCRATCH_TABLE) shouldBe true
    }

    test("a spec's database is dropped once the spec finishes") {
        val finished =
            withSecondSpec { second ->
                second.databaseName().also { live -> postgres.databaseNames() shouldContain live }
            }

        postgres.databaseNames() shouldNotContain finished
    }

    test("the migrated template refuses connections") {
        postgres.acceptsConnections(TEMPLATE_DATABASE) shouldBe false
    }
})

/** Private, so the JUnit runner never discovers it as a spec of its own. */
private class SecondSpec : FunSpec() {
    val postgres = postgresHarness()
}

/**
 * Fires the spec's `afterSpec` listeners by hand, not through a nested `TestEngineLauncher`: a
 * nested engine reads the same `kotest.filter.*` properties, so a run filtered down to one test
 * here would filter the inner spec as well.
 */
private suspend fun <T> withSecondSpec(assertions: suspend (PostgresHarness) -> T): T {
    val spec = SecondSpec()
    return try {
        assertions(spec.postgres)
    } finally {
        spec.registeredExtensions().filterIsInstance<AfterSpecListener>().forEach { it.afterSpec(spec) }
    }
}

private fun PostgresHarness.databaseName(): String = scalar("SELECT current_database()")

private fun PostgresHarness.databaseNames(): List<String> =
    dsl.resultQuery("SELECT datname FROM pg_database").fetch(0, String::class.java)

/** Assigned by `initdb`, so two containers never share one. */
private fun PostgresHarness.clusterIdentifier(): Long = scalar("SELECT system_identifier FROM pg_control_system()")

private fun PostgresHarness.hasTable(name: String): Boolean = scalar("SELECT to_regclass(?) IS NOT NULL", name)

private fun PostgresHarness.acceptsConnections(database: String): Boolean =
    scalar("SELECT datallowconn FROM pg_database WHERE datname = ?", database)

private inline fun <reified T : Any> PostgresHarness.scalar(
    sql: String,
    vararg bindings: Any,
): T = checkNotNull(dsl.resultQuery(sql, *bindings).fetchSingle(0, T::class.javaObjectType)) { "no value from: $sql" }
