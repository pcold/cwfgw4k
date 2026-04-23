package com.cwfgw.jooqcodegen

import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.containers.PostgreSQLContainer

private const val SCHEMA = "cwfgw4k"
private const val POSTGRES_IMAGE = "postgres:18-alpine"

fun main(args: Array<String>) {
    require(args.size == 3) {
        "Usage: JooqCodegen <migrations-dir> <output-dir> <package-name>"
    }
    val migrationsDir = args[0]
    val outputDir = args[1]
    val packageName = args[2]

    PostgreSQLContainer<Nothing>(POSTGRES_IMAGE).use { postgres ->
        postgres.start()
        runFlyway(postgres, migrationsDir)
        GenerationTool.generate(buildJooqConfig(postgres, outputDir, packageName))
    }
}

private fun runFlyway(
    postgres: PostgreSQLContainer<Nothing>,
    migrationsDir: String,
) {
    Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .schemas(SCHEMA)
        .createSchemas(true)
        .locations("filesystem:$migrationsDir")
        .load()
        .migrate()
}

private fun buildJooqConfig(
    postgres: PostgreSQLContainer<Nothing>,
    outputDir: String,
    packageName: String,
): Configuration =
    Configuration()
        .withJdbc(
            Jdbc()
                .withDriver("org.postgresql.Driver")
                .withUrl(postgres.jdbcUrl)
                .withUser(postgres.username)
                .withPassword(postgres.password),
        )
        .withGenerator(
            Generator()
                .withName("org.jooq.codegen.KotlinGenerator")
                .withDatabase(
                    Database()
                        .withName("org.jooq.meta.postgres.PostgresDatabase")
                        .withInputSchema(SCHEMA)
                        .withIncludes(".*")
                        .withExcludes("flyway_schema_history"),
                )
                .withGenerate(
                    Generate()
                        .withKotlinNotNullPojoAttributes(true)
                        .withKotlinNotNullRecordAttributes(true)
                        .withPojosAsKotlinDataClasses(true),
                )
                .withTarget(
                    Target()
                        .withPackageName(packageName)
                        .withDirectory(outputDir),
                ),
        )
