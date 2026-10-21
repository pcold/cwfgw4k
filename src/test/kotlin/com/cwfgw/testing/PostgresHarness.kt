package com.cwfgw.testing

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import io.kotest.extensions.testcontainers.toDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val SCHEMA = "cwfgw4k"
private const val IMAGE = "postgres:18-alpine"
internal const val TEMPLATE_DATABASE = "migrated_template"

/**
 * Tables excluded from automatic reset between tests. Add tables here that hold
 * reference data loaded by migrations and expected to persist across tests.
 */
private val PRESERVED_TABLES = setOf("flyway_schema_history", "cache_generation")

/**
 * [dataSource] reaches a database only this spec uses. [reset] runs before every test and only empties
 * the migrated schema's tables: anything else a spec creates stays until its database is dropped.
 */
internal class PostgresHarness(
    val dataSource: HikariDataSource,
) {
    val dsl: DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    val maxPoolSize: Int = dataSource.maximumPoolSize

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
 * One container per test JVM. `install` starts it unsynchronized, which is safe while specs run one
 * at a time in a fork: there is no Kotest `ProjectConfig`, so `maxParallelForks` is the only parallelism.
 */
private val sharedPostgres = PostgreSQLContainer<Nothing>(IMAGE)

private val sharedPostgresExtension =
    JdbcDatabaseContainerExtension(container = sharedPostgres, afterStart = ::migrateTemplateDatabase)

private val specDatabaseCount = AtomicInteger()

/**
 * Neither extension signal says the template is ready: it counts the container started before
 * `afterStart` runs, so a failed migration is not retried, and reruns `afterStart` when a Docker inspect errors.
 */
private val templateMigrated = AtomicBoolean(false)

/**
 * A database per spec is what lets the container be shared: [PostgresHarness.reset] clears rows, so
 * anything else a spec creates would otherwise reach every later spec in the fork.
 */
internal fun Spec.postgresHarness(): PostgresHarness {
    install(sharedPostgresExtension)
    check(templateMigrated.get()) {
        "the shared template never migrated: the first Postgres-backed spec in this JVM carries the failure"
    }

    val database = "spec_${specDatabaseCount.incrementAndGet()}"
    executeOnCluster("""CREATE DATABASE "$database" TEMPLATE "$TEMPLATE_DATABASE"""")

    val dataSource = sharedPostgres.toDataSource { jdbcUrl = databaseUrl(database) }
    afterSpec {
        dataSource.close()
        // FORCE, because a connection the spec opened outside this pool would otherwise fail the drop.
        executeOnCluster("""DROP DATABASE "$database" WITH (FORCE)""")
    }

    val harness = PostgresHarness(dataSource)
    beforeEach { harness.reset() }
    return harness
}

/**
 * `CREATE DATABASE … TEMPLATE` refuses a source with a session attached, so the template is closed
 * to connections as soon as it is migrated: no spec can hold one open on it.
 */
internal fun migrateTemplateDatabase() {
    if (templateMigrated.get()) return
    executeOnCluster("""CREATE DATABASE "$TEMPLATE_DATABASE"""")
    migrationsInto(SCHEMA)
        .dataSource(databaseUrl(TEMPLATE_DATABASE), sharedPostgres.username, sharedPostgres.password)
        .load()
        .migrate()
    executeOnCluster("""ALTER DATABASE "$TEMPLATE_DATABASE" WITH ALLOW_CONNECTIONS false""")
    templateMigrated.set(true)
}

/** A migration spec's own [schema], stopped at [version]: the harness's schema is already at the latest one. */
internal fun PostgresHarness.migrateSchemaTo(
    schema: String,
    version: String,
) {
    migrationsInto(schema)
        .dataSource(dataSource)
        .target(version)
        .load()
        .migrate()
}

private fun migrationsInto(schema: String): FluentConfiguration =
    Flyway.configure()
        .schemas(schema)
        .createSchemas(true)
        .locations("classpath:db/migration")

private fun databaseUrl(database: String): String =
    "jdbc:postgresql://${sharedPostgres.host}:${sharedPostgres.firstMappedPort}/$database"

/** Postgres rejects CREATE and DROP DATABASE inside a transaction block: keep this on autocommit. */
private fun executeOnCluster(sql: String) {
    sharedPostgres.createConnection("").use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }
}
