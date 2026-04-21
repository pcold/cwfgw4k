package com.cwfgw.db

import com.cwfgw.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

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
