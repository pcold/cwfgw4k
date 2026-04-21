package com.cwfgw

import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.health.DatabaseHealthProbe
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer

private const val SCHEMA = "cwfgw4k"

class AppBootSpec : FunSpec({

    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    lateinit var database: Database

    fun overrides(): Map<String, Any> =
        mapOf(
            "db.jdbcUrl" to postgres.jdbcUrl,
            "db.user" to postgres.username,
            "db.password" to postgres.password,
            "db.schema" to SCHEMA,
        )

    beforeSpec {
        postgres.start()
    }

    afterSpec {
        try {
            (database.dataSource as? HikariDataSource)?.close()
        } catch (_: UninitializedPropertyAccessException) {
            // Database never started (earlier test failed); nothing to close.
        }
        postgres.stop()
    }

    test("AppConfig.load merges yaml defaults with runtime overrides") {
        val config = AppConfig.load(overrides())

        config.db.jdbcUrl shouldBe postgres.jdbcUrl
        config.db.user shouldBe postgres.username
        config.db.password shouldBe postgres.password
        config.db.schema shouldBe SCHEMA
        config.http.host shouldBe "0.0.0.0"
    }

    test("Database.start runs Flyway and exposes a working DSLContext") {
        database = Database.start(AppConfig.load(overrides()).db)

        DSL.using(database.dataSource, SQLDialect.POSTGRES).selectOne().fetch().size shouldBe 1
    }

    test("DatabaseHealthProbe reports connected against a live database") {
        val probe = DatabaseHealthProbe(database.dsl)

        probe.isDatabaseConnected() shouldBe true
    }

    test("DatabaseHealthProbe reports disconnected after its pool is closed") {
        val throwawayPool =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = postgres.jdbcUrl
                    username = postgres.username
                    password = postgres.password
                },
            )
        val probe = DatabaseHealthProbe(DSL.using(throwawayPool, SQLDialect.POSTGRES))
        throwawayPool.close()

        probe.isDatabaseConnected() shouldBe false
    }
})
