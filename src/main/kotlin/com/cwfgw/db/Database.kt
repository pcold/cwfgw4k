package com.cwfgw.db

import com.cwfgw.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

private const val HIKARI_KEEPALIVE_MS: Long = 60_000
private const val HIKARI_MAX_LIFETIME_MS: Long = 600_000

class Database(val dataSource: DataSource, val dsl: DSLContext) {
    companion object {
        fun start(cfg: DbConfig): Database {
            val hikari =
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = cfg.jdbcUrl
                        username = cfg.user
                        password = cfg.password
                        maximumPoolSize = cfg.maxPoolSize
                        schema = cfg.schema
                        // Cloud SQL closes idle connections server-side after a few minutes;
                        // without these the pool fills with dead sockets that fail validation
                        // on handout, the rebuild rate falls behind a fan-out burst, and
                        // requests queue past Hikari's 30s connectionTimeout. The May 3 wedge
                        // and the May 4 stress reproduction (both showed
                        // `total=0, active=0, idle=0` at the moment of failure) are this bug.
                        // - keepaliveTime: ping idle connections at 60s so Cloud SQL doesn't
                        //   reach its idle-disconnect threshold for them.
                        // - maxLifetime: proactively recycle at 10m so connections are never
                        //   older than what Cloud SQL might decide to close.
                        keepaliveTime = HIKARI_KEEPALIVE_MS
                        maxLifetime = HIKARI_MAX_LIFETIME_MS
                    },
                )
            Flyway.configure()
                .dataSource(hikari)
                .schemas(cfg.schema)
                .defaultSchema(cfg.schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            return Database(hikari, DSL.using(hikari, SQLDialect.POSTGRES))
        }
    }
}
