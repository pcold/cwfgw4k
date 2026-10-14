package com.cwfgw.db

import com.cwfgw.testing.postgresHarness
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PoolMetricsSpec : FunSpec({

    val postgres = postgresHarness()

    fun throwawayPool(): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgres.dataSource.jdbcUrl
                username = postgres.dataSource.username
                password = postgres.dataSource.password
                minimumIdle = 0
            },
        )

    test("snapshot counts a held connection as active") {
        throwawayPool().use { pool ->
            pool.connection.use {
                val snapshot = PoolMetrics(pool).snapshot()

                snapshot.active shouldBe 1
                snapshot.waiting shouldBe 0
            }
        }
    }

    // The pool-metrics loop in App.kt has no catch on the strength of this: Hikari never nulls the
    // pool behind getHikariPoolMXBean, so even a closed pool reads rather than throws.
    test("snapshot of a closed pool reads empty counters rather than throwing") {
        val pool = throwawayPool()
        pool.connection.close()
        pool.close()

        PoolMetrics(pool).snapshot() shouldBe PoolSnapshot(active = 0, idle = 0, waiting = 0, total = 0)
    }
})
