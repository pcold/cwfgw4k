package com.cwfgw.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource

data class AppConfig(
    val http: HttpConfig,
    val db: DbConfig,
) {
    companion object {
        fun load(): AppConfig =
            ConfigLoaderBuilder.default()
                .addResourceSource("/application.yaml")
                .build()
                .loadConfigOrThrow<AppConfig>()
    }
}

data class HttpConfig(val host: String, val port: Int)

data class DbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val schema: String,
    val maxPoolSize: Int,
)
