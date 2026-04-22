package com.cwfgw.testing

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer

private const val SCHEMA = "cwfgw4k"
private const val DEFAULT_IMAGE = "postgres:16-alpine"

/**
 * Tables excluded from automatic reset between tests. Add tables here that hold
 * reference data loaded by migrations and expected to persist across tests.
 */
private val PRESERVED_TABLES = setOf("flyway_schema_history")

/**
 * Spec-scoped Postgres + Flyway setup for repository integration specs. A single container
 * starts for the spec, Flyway applies every migration once, and [dsl] / [dataSource] are
 * available to every test. Call [reset] in `beforeTest` to reset state between tests.
 */
internal class PostgresHarness(
    val dataSource: HikariDataSource,
) {
    val dsl: DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    fun reset() {
        val tables =
            dsl.meta()
                .filterSchemas { it.name == SCHEMA }
                .tables
                .map { it.name }
                .filterNot { it in PRESERVED_TABLES }

        if (tables.isEmpty()) return

        val quoted = tables.joinToString(", ") { """"$SCHEMA"."$it"""" }
        dsl.execute("""TRUNCATE TABLE $quoted RESTART IDENTITY CASCADE""")
    }
}

/**
 * Install a Postgres container on this spec that runs Flyway migrations on startup. Returns a
 * [PostgresHarness] exposing the jOOQ DSLContext and connection details.
 */
internal fun Spec.postgresHarness(image: String = DEFAULT_IMAGE): PostgresHarness {
    val container = PostgreSQLContainer<Nothing>(image)
    val extension =
        JdbcDatabaseContainerExtension(
            container = container,
            afterStart = {
                Flyway.configure()
                    .dataSource(container.jdbcUrl, container.username, container.password)
                    .schemas(SCHEMA)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()
            },
        )
    val dataSource = install(extension)
    val harness = PostgresHarness(dataSource)
    beforeEach { harness.reset() }
    return harness
}
