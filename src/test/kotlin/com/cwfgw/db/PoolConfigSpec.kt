package com.cwfgw.db

import com.cwfgw.config.ConfigSecret
import com.cwfgw.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val POOL_SIZE = 15

private val CONFIG =
    DbConfig(
        jdbcUrl = "jdbc:postgresql://localhost:5432/cwfgw",
        user = "cwfgw",
        password = ConfigSecret("not-a-real-password"),
        schema = "cwfgw4k",
        maxPoolSize = POOL_SIZE,
    )

// What the pool will run with: validate() rewrites a value Hikari rejects, such as an idle timeout under its floor.
private fun validatedPoolConfig(): HikariConfig = poolConfig(CONFIG).apply { validate() }

class PoolConfigSpec : FunSpec({

    test("an idle pool keeps no connections and pings none") {
        val config = validatedPoolConfig()

        config.minimumIdle shouldBe 0
        config.idleTimeout shouldBe 30_000L
        config.keepaliveTime shouldBe 0L
    }

    test("the pool is sized by the config, which the Transactor's dispatcher must match") {
        validatedPoolConfig().maximumPoolSize shouldBe POOL_SIZE
    }

    test("every connection bounds its handshake and each read") {
        val properties = validatedPoolConfig().dataSourceProperties

        properties.getProperty("connectTimeout") shouldBe "10"
        properties.getProperty("socketTimeout") shouldBe "30"
    }
})
