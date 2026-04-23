package com.cwfgw

import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.health.DatabaseHealthProbe
import com.cwfgw.testing.postgresHarness
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL

private const val SCHEMA = "cwfgw4k"

class AppBootSpec : FunSpec({

    val postgres = postgresHarness()

    fun overrides(): Map<String, Any> =
        mapOf(
            "db.jdbcUrl" to postgres.dataSource.jdbcUrl,
            "db.user" to postgres.dataSource.username,
            "db.password" to postgres.dataSource.password,
            "db.schema" to SCHEMA,
        )

    test("AppConfig.load merges yaml defaults with runtime overrides") {
        val config = AppConfig.load(overrides())

        config.db.jdbcUrl shouldBe postgres.dataSource.jdbcUrl
        config.db.user shouldBe postgres.dataSource.username
        config.db.password shouldBe postgres.dataSource.password
        config.db.schema shouldBe SCHEMA
        config.http.host shouldBe "0.0.0.0"
    }

    test("Database.start runs Flyway and exposes a working DSLContext") {
        val database = Database.start(AppConfig.load(overrides()).db)
        try {
            DSL.using(database.dataSource, SQLDialect.POSTGRES).selectOne().fetch().size shouldBe 1
        } finally {
            (database.dataSource as HikariDataSource).close()
        }
    }

    test("DatabaseHealthProbe reports connected against a live database") {
        val probe = DatabaseHealthProbe(postgres.dsl)

        probe.isDatabaseConnected() shouldBe true
    }

    test("DatabaseHealthProbe reports disconnected after its pool is closed") {
        val throwawayPool =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = postgres.dataSource.jdbcUrl
                    username = postgres.dataSource.username
                    password = postgres.dataSource.password
                },
            )
        val probe = DatabaseHealthProbe(DSL.using(throwawayPool, SQLDialect.POSTGRES))
        throwawayPool.close()

        probe.isDatabaseConnected() shouldBe false
    }
})
