package com.cwfgw.health

import com.cwfgw.db.Transactor
import com.cwfgw.testing.postgresHarness
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class DatabaseHealthProbeSpec : FunSpec({

    val postgres = postgresHarness()

    test("reports connected against a live database") {
        val probe = DatabaseHealthProbe(HealthRepository(), Transactor(postgres.dsl, postgres.maxPoolSize))

        probe.isDatabaseConnected() shouldBe true
    }

    test("reports disconnected, rather than throwing, once its pool is closed") {
        val throwawayPool =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = postgres.dataSource.jdbcUrl
                    username = postgres.dataSource.username
                    password = postgres.dataSource.password
                    minimumIdle = 0
                },
            )
        val probe =
            DatabaseHealthProbe(
                HealthRepository(),
                Transactor(DSL.using(throwawayPool, SQLDialect.POSTGRES), throwawayPool.maximumPoolSize),
            )
        throwawayPool.close()

        probe.isDatabaseConnected() shouldBe false
    }
})
