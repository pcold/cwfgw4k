package com.cwfgw.seed

import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Development-only seed entrypoint wired into the `seed` Gradle task.
 *
 * Loads the same [AppConfig] as the running server, opens the DB
 * (which also runs every Flyway migration), and is the place where
 * follow-on commits will populate two 2026 seasons (Spring + Summer)
 * via the existing admin services — no HTTP round-trip, just direct
 * service calls. The resulting database state mirrors what an admin
 * would produce by clicking through the UI.
 *
 * This commit ships only the boot loop. Seeding data lands next.
 */
fun main() {
    log.info { "SeedMain: loading config" }
    val config = AppConfig.load()

    log.info { "SeedMain: starting database (runs Flyway migrations against ${config.db.jdbcUrl})" }
    Database.start(config.db)

    log.info { "SeedMain: database ready. Seed data will land in a follow-up commit." }
}
