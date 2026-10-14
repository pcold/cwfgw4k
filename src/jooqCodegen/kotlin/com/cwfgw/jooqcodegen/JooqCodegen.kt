package com.cwfgw.jooqcodegen

import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.containers.PostgreSQLContainer

fun main(args: Array<String>) {
    require(args.size == 3) {
        "Usage: JooqCodegen <migrations-dir> <output-dir> <package-name>"
    }
    val migrationsDir = args[0]
    val outputDir = args[1]
    val packageName = args[2]

    withMigratedPostgres(migrationsDir) { postgres ->
        GenerationTool.generate(buildJooqConfig(postgres, outputDir, packageName))
    }
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
