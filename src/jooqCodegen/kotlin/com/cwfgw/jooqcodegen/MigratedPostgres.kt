package com.cwfgw.jooqcodegen

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

internal const val SCHEMA = "cwfgw4k"
private const val POSTGRES_IMAGE = "postgres:18-alpine"

internal fun <T> withMigratedPostgres(
    migrationsDir: String,
    block: (PostgreSQLContainer<Nothing>) -> T,
): T =
    PostgreSQLContainer<Nothing>(POSTGRES_IMAGE).use { postgres ->
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(SCHEMA)
            .createSchemas(true)
            .locations("filesystem:$migrationsDir")
            .load()
            .migrate()
        block(postgres)
    }
