package com.cwfgw.db

import com.cwfgw.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

private const val PG_CONNECT_TIMEOUT_SECONDS: String = "10"
private const val PG_SOCKET_TIMEOUT_SECONDS: String = "30"

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
                        // Postgres JDBC defaults both timeouts to 0 = wait forever. With
                        // Cloud SQL via the in-process socket factory, occasional Broken
                        // Pipe events during connection setup can otherwise hang a JVM
                        // thread until Cloud Run kills the request at its 90s timeout.
                        // - connectTimeout: TCP+SSL handshake must complete in 10s.
                        // - socketTimeout: a single read (query, response) must complete
                        //   in 30s. Both bound the worst case so a stuck connection fails
                        //   fast as a 500 instead of pegging an instance into 504-land.
                        addDataSourceProperty("connectTimeout", PG_CONNECT_TIMEOUT_SECONDS)
                        addDataSourceProperty("socketTimeout", PG_SOCKET_TIMEOUT_SECONDS)
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
