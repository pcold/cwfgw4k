package com.cwfgw.jooqcodegen

import org.testcontainers.containers.PostgreSQLContainer
import java.io.File

private val GENERATED_HEADER =
    """
    --
    -- Generated from src/main/resources/db/migration by ./gradlew updateSchemaDump.
    -- `check` fails when the two disagree.
    --
    """.trimIndent()

/**
 * pg_dump 18 seeds this psql guard key randomly per run, which alone would make the dump differ from
 * itself. A fixed, public key is safe only because the dump renders our own reviewed migrations.
 */
private const val RESTRICT_KEY = "cwfgw4kschemadump"

private val VERSION_BANNER_PREFIXES =
    listOf("-- Dumped from database version ", "-- Dumped by pg_dump version ")

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: SchemaDump <migrations-dir> <output-file>"
    }
    val migrationsDir = args[0]
    val outputFile = File(args[1])

    val dump =
        try {
            withMigratedPostgres(migrationsDir, ::dumpSchema)
        } catch (refused: SchemaDumpRefused) {
            System.err.println(refused.stderr)
            throw refused
        }

    outputFile.parentFile.mkdirs()
    outputFile.writeText("$GENERATED_HEADER\n\n${withoutVersionBanner(dump)}")
}

/**
 * Run inside the container, so the client version always equals the server's and no host needs
 * `pg_dump` installed.
 */
private fun dumpSchema(postgres: PostgreSQLContainer<Nothing>): String {
    val result =
        postgres.execInContainer(
            "pg_dump",
            "--schema-only",
            "--no-owner",
            "--restrict-key=$RESTRICT_KEY",
            "--username=${postgres.username}",
            "--dbname=${postgres.databaseName}",
        )
    if (result.exitCode != 0) throw SchemaDumpRefused(result.exitCode, result.stderr)
    return result.stdout
}

/**
 * [stderr] stays out of [message] because a subprocess writes what it likes; [main] prints it to the
 * build's error stream, the one place an operator reads it, since a throwable prints no fields.
 */
private class SchemaDumpRefused(
    exitCode: Int,
    val stderr: String,
) : IllegalStateException("pg_dump exited $exitCode")

/**
 * `postgres:18-alpine` floats, so a republished image would move these two lines and turn the gate red
 * on a commit that changed nothing. [GENERATED_HEADER] replaces the introduction dropped with them.
 */
private fun withoutVersionBanner(dump: String): String =
    dump
        .lineSequence()
        .filterNot { line -> VERSION_BANNER_PREFIXES.any(line::startsWith) }
        .dropWhile { it.isBlank() || it.startsWith("--") }
        .joinToString("\n")
